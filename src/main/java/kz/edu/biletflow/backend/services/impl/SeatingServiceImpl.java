package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.SeatMapResponse;
import kz.edu.biletflow.backend.dtos.SeatSectionResponse;
import kz.edu.biletflow.backend.dtos.SeatStatus;
import kz.edu.biletflow.backend.dtos.SeatingLayoutResponse;
import kz.edu.biletflow.backend.dtos.SectionSeatsResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.SeatLabels;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.EventSeatRepository;
import kz.edu.biletflow.backend.repositories.EventSectionRepository;
import kz.edu.biletflow.backend.repositories.OrderItemRepository;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.SeatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatingServiceImpl implements SeatingService {
    private static final int INSERT_BATCH_SIZE = 1000;
    private static final int LABELS_IN_DESCRIPTION = 10;

    private final SeatingLayouts layouts;
    private final EventRepository eventRepository;
    private final EventSectionRepository sectionRepository;
    private final EventSeatRepository seatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SeatingLayoutResponse> getLayouts() {
        return layouts.all().stream().map(SeatingServiceImpl::toLayoutResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponse getSeating(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = getEvent(eventId);
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw notFound(eventId);
        }
        return toSeatMap(event, true);
    }

    @Override
    @Transactional
    public SeatMapResponse setSeatingMode(Long organizerId, Long eventId, SeatingMode mode) {
        Event event = getOwnedEditableEvent(organizerId, eventId);
        if (event.getSeatingMode() == mode) {
            throw new BusinessRuleViolationException("The event already uses " + describe(mode));
        }
        // An order being placed right now holds its ticket type locks until it commits, so after taking them
        // the check below also sees it.
        List<TicketType> ticketTypes = lockAllTicketTypes(event);
        if (orderRepository.existsByEventId(eventId)) {
            throw new BusinessRuleViolationException("The seating can't change once the event has orders");
        }

        String description;
        if (mode == SeatingMode.ASSIGNED) {
            SeatingLayouts.Layout layout = venueLayout(event);
            createSeatMap(event, layout);
            event.setCapacity(layout.capacity());
            // Quantities now come from the seat map: a ticket type sells what is linked to it.
            ticketTypes.forEach(t -> t.setQuantity(0));
            description = "Switched to assigned seating with the '" + layout.name() + "' seat map ("
                    + layout.seatCount() + " seats, " + layout.standingCapacity() + " standing places)";
        } else {
            seatRepository.deleteAllByEvent(eventId);
            sectionRepository.deleteAllByEvent(eventId);
            description = "Switched to general admission; the seat map was removed";
        }
        event.setSeatingMode(mode);
        eventRepository.save(event);
        auditService.record(organizerId, AuditAction.SEATING_MODE_CHANGED, eventId, eventId, description);
        return toSeatMap(event, true);
    }

    @Override
    @Transactional
    public SeatMapResponse assignSection(Long organizerId, Long eventId, Long sectionId, @Nullable Long ticketTypeId) {
        Event event = getOwnedEditableEvent(organizerId, eventId);
        ensureAssigned(event);
        EventSection section = sectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));
        TicketType oldType = section.getTicketType();
        if (ticketTypeId != null) {
            TicketType candidate = ticketTypeRepository.findById(ticketTypeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + ticketTypeId));
            if (!candidate.getEvent().getId().equals(eventId)) {
                throw new BusinessRuleViolationException("Ticket type " + ticketTypeId + " does not belong to this event");
            }
        }
        if (oldType != null && oldType.getId().equals(ticketTypeId)) {
            return toSeatMap(event, true);
        }

        // Same lock order as an order (ticket types by id), so a sale and this change run one after the other.
        Map<Long, TicketType> locked = lockTicketTypes(idsOf(oldType == null ? null : oldType.getId(), ticketTypeId));
        LocalDateTime now = LocalDateTime.now();
        if (orderItemRepository.existsClaimInSection(sectionId, OrderStatus.PAID, OrderStatus.PENDING, now)) {
            throw new BusinessRuleViolationException("'" + section.getName()
                    + "' already has sold or reserved tickets, so its ticket type can't change");
        }
        TicketType newType = ticketTypeId == null ? null : locked.get(ticketTypeId);
        if (newType != null) {
            List<EventSection> linked = sectionRepository.findAllByTicketTypeId(newType.getId()).stream()
                    .filter(s -> !s.getId().equals(sectionId))
                    .toList();
            if (section.getKind() == SectionKind.STANDING && !linked.isEmpty()) {
                throw new BusinessRuleViolationException("A standing section needs a ticket type of its own: '"
                        + newType.getName() + "' already sells " + names(linked));
            }
            if (section.getKind() == SectionKind.SEATED && linked.stream().anyMatch(s -> s.getKind() == SectionKind.STANDING)) {
                throw new BusinessRuleViolationException("'" + newType.getName()
                        + "' sells a standing section, so it can't also sell seats");
            }
        }

        section.setTicketType(newType);
        sectionRepository.save(section);
        locked.values().forEach(this::syncQuantity);
        auditService.record(organizerId, AuditAction.SEATING_SECTION_ASSIGNED, sectionId, eventId,
                "Section '" + section.getName() + "' " + (newType == null
                        ? "taken off sale"
                        : "now sells '" + newType.getName() + "' at " + newType.getPrice() + " KZT"));
        return toSeatMap(event, true);
    }

    @Override
    @Transactional
    public SeatMapResponse setSeatsBlocked(Long organizerId, Long eventId, List<Long> seatIds, boolean blocked) {
        Event event = getOwnedEditableEvent(organizerId, eventId);
        ensureAssigned(event);
        Set<Long> ids = new TreeSet<>(seatIds);
        List<EventSeat> seats = seatRepository.findAllForEvent(eventId, ids);
        if (seats.size() != ids.size()) {
            Set<Long> found = seats.stream().map(EventSeat::getId).collect(Collectors.toSet());
            Long missing = ids.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Seat not found with id: " + missing);
        }

        // Lock order of an order: ticket types first, then the seats.
        SortedSet<Long> typeIds = seats.stream()
                .map(s -> s.getSection().getTicketType())
                .filter(Objects::nonNull)
                .map(TicketType::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<Long, TicketType> locked = lockTicketTypes(typeIds);
        seatRepository.lockAll(ids);
        if (blocked) {
            Set<Long> claimed = claimedSeatIds(ids, LocalDateTime.now());
            List<String> taken = seats.stream().filter(s -> claimed.contains(s.getId())).map(SeatingServiceImpl::label).toList();
            if (!taken.isEmpty()) {
                throw new BusinessRuleViolationException("Sold or reserved seats can't be blocked: " + summarize(taken));
            }
        }

        List<EventSeat> changed = seats.stream().filter(s -> s.isBlocked() != blocked).toList();
        changed.forEach(s -> s.setBlocked(blocked));
        seatRepository.saveAll(changed);
        locked.values().forEach(this::syncQuantity);
        if (!changed.isEmpty()) {
            auditService.record(organizerId, blocked ? AuditAction.SEATS_BLOCKED : AuditAction.SEATS_UNBLOCKED, eventId, eventId,
                    changed.size() + (changed.size() == 1 ? " seat " : " seats ") + (blocked ? "taken off sale: " : "put back on sale: ")
                            + summarize(changed.stream().map(SeatingServiceImpl::label).toList()));
        }
        return toSeatMap(event, true);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getVisibleEvent(eventId, requesterId, isPlatformAdmin);
        ensureHasSeatMap(event);
        return toSeatMap(event, isOwnerOrAdmin(event, requesterId, isPlatformAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public SectionSeatsResponse getSectionSeats(Long eventId, Long sectionId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getVisibleEvent(eventId, requesterId, isPlatformAdmin);
        ensureHasSeatMap(event);
        boolean fullView = isOwnerOrAdmin(event, requesterId, isPlatformAdmin);
        EventSection section = sectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));
        LocalDateTime now = LocalDateTime.now();

        SectionSeatsResponse response = new SectionSeatsResponse();
        response.setSection(toSectionResponse(section, seatCounts(eventId).get(sectionId), sectionSales(eventId, now).get(sectionId), fullView));
        if (section.getKind() == SectionKind.STANDING) {
            response.setSeats(List.of());
            return response;
        }

        Map<Long, Object[]> claims = new HashMap<>();
        for (Object[] row : orderItemRepository.findSeatClaimsInSection(sectionId, OrderStatus.PAID, OrderStatus.PENDING, now)) {
            claims.put((Long) row[0], row);
        }
        boolean onSale = response.getSection().isOnSale();
        List<SectionSeatsResponse.Seat> seats = new ArrayList<>();
        for (EventSeat seat : seatRepository.findAllBySectionIdOrderByIdAsc(sectionId)) {
            SectionSeatsResponse.Seat dto = new SectionSeatsResponse.Seat();
            dto.setId(seat.getId());
            dto.setRow(seat.getRowLabel());
            dto.setNumber(seat.getSeatNumber());
            dto.setX(seat.getX());
            dto.setY(seat.getY());
            dto.setAccessible(seat.isAccessible());
            Object[] claim = claims.get(seat.getId());
            if (claim != null) {
                boolean paid = claim[1] == OrderStatus.PAID;
                boolean mine = requesterId != null && requesterId.equals(claim[2]);
                dto.setStatus(paid ? SeatStatus.SOLD : mine ? SeatStatus.YOURS : SeatStatus.HELD);
            } else {
                dto.setStatus(seat.isBlocked() || !onSale ? SeatStatus.UNAVAILABLE : SeatStatus.AVAILABLE);
            }
            seats.add(dto);
        }
        response.setSeats(seats);
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void copySeating(Event original, Event copy) {
        if (original.getSeatingMode() != SeatingMode.ASSIGNED) {
            return;
        }
        Optional<SeatingLayouts.Layout> layout = layouts.find(copy.getVenue().getSeatingLayout());
        if (layout.isEmpty()) {
            return; // the venue lost its seat map since: the copy starts with general admission
        }
        createSeatMap(copy, layout.get());
        copy.setSeatingMode(SeatingMode.ASSIGNED);
        copy.setCapacity(layout.get().capacity());
        eventRepository.save(copy);

        List<Object[]> blocked = seatRepository.findBlockedByEvent(original.getId()).stream()
                .map(s -> new Object[]{copy.getId(), s.getSection().getSectionKey(), s.getRowLabel(), s.getSeatNumber()})
                .toList();
        if (!blocked.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    UPDATE event_seats s SET blocked = true FROM event_sections sec
                    WHERE s.section_id = sec.id AND sec.event_id = ? AND sec.section_key = ?
                      AND s.row_label = ? AND s.seat_number = ?
                    """, blocked);
        }
    }

    // seat map creation //

    // Sections through JPA (a handful), seats through one JDBC batch: a stadium has tens of thousands.
    private void createSeatMap(Event event, SeatingLayouts.Layout layout) {
        int order = 0;
        List<Object[]> seatRows = new ArrayList<>();
        for (SeatingLayouts.Section definition : layout.sections()) {
            EventSection section = new EventSection();
            section.setEvent(event);
            section.setSectionKey(definition.key());
            section.setName(definition.name());
            section.setKind(definition.kind());
            section.setCapacity(definition.capacity());
            section.setX(definition.x());
            section.setY(definition.y());
            section.setWidth(definition.width());
            section.setHeight(definition.height());
            section.setSortOrder(order++);
            EventSection saved = sectionRepository.save(section); // identity id: inserted right away
            for (SeatingLayouts.Seat seat : definition.seats()) {
                seatRows.add(new Object[]{event.getId(), saved.getId(), seat.row(), seat.number(), seat.x(), seat.y(), seat.accessible()});
            }
        }
        for (int from = 0; from < seatRows.size(); from += INSERT_BATCH_SIZE) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO event_seats (event_id, section_id, row_label, seat_number, x, y, accessible, blocked)
                    VALUES (?, ?, ?, ?, ?, ?, ?, false)
                    """, seatRows.subList(from, Math.min(from + INSERT_BATCH_SIZE, seatRows.size())));
        }
    }

    // A ticket type sells the seats linked to it that aren't blocked, plus its standing section's places.
    private void syncQuantity(TicketType ticketType) {
        long seats = seatRepository.countOnSaleByTicketType(ticketType.getId());
        long standing = sectionRepository.sumCapacityByTicketType(ticketType.getId(), SectionKind.STANDING);
        ticketType.setQuantity(Math.toIntExact(seats + standing));
        ticketTypeRepository.save(ticketType);
    }

    // views //

    private SeatMapResponse toSeatMap(Event event, boolean fullView) {
        SeatMapResponse map = new SeatMapResponse();
        map.setEventId(event.getId());
        map.setSeatingMode(event.getSeatingMode());
        map.setCapacity(event.getCapacity());
        // In general admission, the organizer still sees which seat map the venue would give.
        layouts.find(event.getVenue().getSeatingLayout()).ifPresent(layout -> {
            map.setLayoutKey(layout.key());
            map.setLayoutName(layout.name());
            map.setWidth(layout.width());
            map.setHeight(layout.height());
            if (layout.stage() != null) {
                SeatMapResponse.Area stage = new SeatMapResponse.Area();
                stage.setLabel(layout.stage().label());
                stage.setX(layout.stage().x());
                stage.setY(layout.stage().y());
                stage.setWidth(layout.stage().width());
                stage.setHeight(layout.stage().height());
                map.setStage(stage);
            }
        });
        if (event.getSeatingMode() != SeatingMode.ASSIGNED) {
            map.setSections(List.of());
            return map;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, long[]> counts = seatCounts(event.getId());
        Map<Long, long[]> sales = sectionSales(event.getId(), now);
        map.setSections(sectionRepository.findAllByEventIdOrderBySortOrderAsc(event.getId()).stream()
                .map(s -> toSectionResponse(s, counts.get(s.getId()), sales.get(s.getId()), fullView))
                .toList());
        return map;
    }

    private SeatSectionResponse toSectionResponse(EventSection section, @Nullable long[] seatCount, @Nullable long[] sale,
                                                  boolean fullView) {
        SeatSectionResponse dto = new SeatSectionResponse();
        dto.setId(section.getId());
        dto.setKey(section.getSectionKey());
        dto.setName(section.getName());
        dto.setKind(section.getKind());
        dto.setX(section.getX());
        dto.setY(section.getY());
        dto.setWidth(section.getWidth());
        dto.setHeight(section.getHeight());
        dto.setCapacity(section.getCapacity());

        TicketType type = section.getTicketType();
        boolean onSale = type != null && !type.isHidden();
        dto.setOnSale(onSale);
        if (type != null && (onSale || fullView)) { // a hidden ticket type stays hidden from buyers
            dto.setTicketTypeId(type.getId());
            dto.setTicketTypeName(type.getName());
            dto.setPrice(type.getPrice());
        }
        long sold = sale != null ? sale[0] : 0;
        long held = sale != null ? sale[1] : 0;
        long blocked = seatCount != null ? seatCount[1] : 0;
        dto.setSold(sold);
        dto.setHeld(held);
        dto.setBlocked(blocked);
        dto.setAvailable(onSale ? Math.max(section.getCapacity() - blocked - sold - held, 0) : 0);
        return dto;
    }

    // sectionId -> [seats, blocked]
    private Map<Long, long[]> seatCounts(Long eventId) {
        Map<Long, long[]> result = new HashMap<>();
        for (Object[] row : seatRepository.countBySection(eventId)) {
            result.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return result;
    }

    // sectionId -> [sold, held]
    private Map<Long, long[]> sectionSales(Long eventId, LocalDateTime now) {
        Map<Long, long[]> result = new HashMap<>();
        for (Object[] row : orderItemRepository.aggregateBySection(eventId, OrderStatus.PAID, OrderStatus.PENDING, now)) {
            result.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return result;
    }

    private static SeatingLayoutResponse toLayoutResponse(SeatingLayouts.Layout layout) {
        SeatingLayoutResponse dto = new SeatingLayoutResponse();
        dto.setKey(layout.key());
        dto.setName(layout.name());
        dto.setDescription(layout.description());
        dto.setCapacity(layout.capacity());
        dto.setSeatCount(layout.seatCount());
        dto.setStandingCapacity(layout.standingCapacity());
        dto.setSections(layout.sections().stream().map(s -> {
            SeatingLayoutResponse.Section section = new SeatingLayoutResponse.Section();
            section.setKey(s.key());
            section.setName(s.name());
            section.setKind(s.kind());
            section.setCapacity(s.capacity());
            return section;
        }).toList());
        return dto;
    }

    // helpers //

    private Set<Long> claimedSeatIds(Collection<Long> seatIds, LocalDateTime now) {
        return orderItemRepository.findSeatClaims(seatIds, OrderStatus.PAID, OrderStatus.PENDING, now).stream()
                .map(row -> (Long) row[0])
                .collect(Collectors.toSet());
    }

    private List<TicketType> lockAllTicketTypes(Event event) {
        TreeSet<Long> ids = ticketTypeRepository.findAllByEventIdOrderByIdAsc(event.getId()).stream()
                .map(TicketType::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        return new ArrayList<>(lockTicketTypes(ids).values());
    }

    private Map<Long, TicketType> lockTicketTypes(SortedSet<Long> ids) {
        Map<Long, TicketType> locked = new LinkedHashMap<>();
        for (Long id : ids) {
            locked.put(id, ticketTypeRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + id)));
        }
        return locked;
    }

    private static SortedSet<Long> idsOf(@Nullable Long first, @Nullable Long second) {
        SortedSet<Long> ids = new TreeSet<>();
        if (first != null) {
            ids.add(first);
        }
        if (second != null) {
            ids.add(second);
        }
        return ids;
    }

    private SeatingLayouts.Layout venueLayout(Event event) {
        Venue venue = event.getVenue();
        if (venue.getSeatingLayout() == null) {
            throw new BusinessRuleViolationException("The venue '" + venue.getName() + "' has no seat map. Choose a venue"
                    + " with a seating layout, or keep general admission, which also suits a venue without seats");
        }
        return layouts.find(venue.getSeatingLayout()).orElseThrow(() -> new BusinessRuleViolationException(
                "The venue's seat map '" + venue.getSeatingLayout() + "' no longer exists"));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> notFound(eventId));
    }

    private Event getOwnedEditableEvent(Long organizerId, Long eventId) {
        Event event = getEvent(eventId);
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this event");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("The seating of a cancelled event cannot be changed");
        }
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("This event is suspended by a platform administrator");
        }
        return event;
    }

    // Same rule as the ticket type list: don't reveal that an invisible event exists.
    private Event getVisibleEvent(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getEvent(eventId);
        boolean publiclyVisible = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() != VisibilityStatus.PRIVATE
                && !event.isSuspended();
        if (!publiclyVisible && !isOwnerOrAdmin(event, requesterId, isPlatformAdmin)) {
            throw notFound(eventId);
        }
        return event;
    }

    private static boolean isOwnerOrAdmin(Event event, @Nullable Long requesterId, boolean isPlatformAdmin) {
        return isPlatformAdmin || (requesterId != null && event.getOrganizer().getId().equals(requesterId));
    }

    private static void ensureAssigned(Event event) {
        if (event.getSeatingMode() != SeatingMode.ASSIGNED) {
            throw new BusinessRuleViolationException("The event uses general admission: switch it to assigned seating first");
        }
    }

    private static void ensureHasSeatMap(Event event) {
        if (event.getSeatingMode() != SeatingMode.ASSIGNED) {
            throw new BusinessRuleViolationException("This event has general admission: there is no seat map, order ticket types instead");
        }
    }

    private static String describe(SeatingMode mode) {
        return mode == SeatingMode.ASSIGNED ? "assigned seating" : "general admission";
    }

    private static String label(EventSeat seat) {
        return SeatLabels.describe(seat.getSection().getName(), seat.getRowLabel(), seat.getSeatNumber());
    }

    private static String names(List<EventSection> sections) {
        return sections.stream().map(s -> "'" + s.getName() + "'").collect(Collectors.joining(", "));
    }

    static String summarize(List<String> labels) {
        String shown = labels.stream().limit(LABELS_IN_DESCRIPTION).collect(Collectors.joining("; "));
        return labels.size() > LABELS_IN_DESCRIPTION ? shown + " and " + (labels.size() - LABELS_IN_DESCRIPTION) + " more" : shown;
    }

    private static ResourceNotFoundException notFound(Long eventId) {
        return new ResourceNotFoundException("Event not found with id: " + eventId);
    }
}
