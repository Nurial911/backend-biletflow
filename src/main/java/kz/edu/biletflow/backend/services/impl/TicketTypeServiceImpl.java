package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.TicketTypeMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrderItemRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {
    private static final Long NO_ID = -1L;

    private final TicketTypeMapper ticketTypeMapper;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public TicketTypeResponse createTicketType(Long organizerId, Long eventId, CreateTicketTypeRequest request) {
        Event event = getOwnedEvent(organizerId, eventId);
        ensureEventEditable(event);

        TicketType ticketType = ticketTypeMapper.toEntity(request);
        ticketType.setEvent(event);

        validateSalesWindow(ticketType.getSalesStart(), ticketType.getSalesEnd(), event);
        validateCapacity(event, ticketType.getQuantity(), NO_ID);

        TicketType savedTicketType = ticketTypeRepository.save(ticketType);
        audit(organizerId, AuditAction.TICKET_TYPE_CREATED, savedTicketType,
                "Ticket type '" + savedTicketType.getName() + "' created: price " + savedTicketType.getPrice()
                        + " KZT, quantity " + savedTicketType.getQuantity());
        return toResponse(savedTicketType);
    }

    @Override
    @Transactional
    public List<TicketTypeResponse> getTicketTypesForEvent(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        boolean isOwner = requesterId != null && event.getOrganizer().getId().equals(requesterId);
        boolean canSeeHidden = isOwner || isPlatformAdmin;
        boolean eventPubliclyVisible = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() != VisibilityStatus.PRIVATE
                && !event.isSuspended();

        // Same rule as getEventById: don't reveal that an invisible event exists.
        if (!canSeeHidden && !eventPubliclyVisible) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }

        List<TicketType> ticketTypes = canSeeHidden
                ? ticketTypeRepository.findAllByEventIdOrderByIdAsc(eventId)
                : ticketTypeRepository.findAllByEventIdAndHiddenFalseOrderByIdAsc(eventId);

        return ticketTypes.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public TicketTypeResponse updateTicketType(Long organizerId, Long ticketTypeId, UpdateTicketTypeRequest request) {
        TicketType ticketType = getOwnedTicketType(organizerId, ticketTypeId);
        Event event = ticketType.getEvent();
        ensureEventEditable(event);

        long sold = countSold(ticketType);
        if (sold > 0 && request.getPrice() != null && request.getPrice().compareTo(ticketType.getPrice()) != 0) {
            throw new BusinessRuleViolationException("Price cannot be changed after tickets have been sold or reserved");
        }
        if (request.getQuantity() != null && request.getQuantity() < sold) {
            throw new BusinessRuleViolationException("Quantity cannot be lower than already sold or reserved: " + sold);
        }
        BigDecimal oldPrice = ticketType.getPrice();
        int oldQuantity = ticketType.getQuantity();
        ticketTypeMapper.updateTicketType(request, ticketType);

        // Validate the merged result so changing a single field is still checked.
        validateSalesWindow(ticketType.getSalesStart(), ticketType.getSalesEnd(), event);
        validateCapacity(event, ticketType.getQuantity(), ticketType.getId());

        TicketType saved = ticketTypeRepository.save(ticketType);
        StringBuilder description = new StringBuilder("Ticket type '" + saved.getName() + "' updated");
        if (oldPrice.compareTo(saved.getPrice()) != 0) {
            description.append(", price ").append(oldPrice).append(" -> ").append(saved.getPrice()).append(" KZT");
        }
        if (oldQuantity != saved.getQuantity()) {
            description.append(", quantity ").append(oldQuantity).append(" -> ").append(saved.getQuantity());
        }
        audit(organizerId, AuditAction.TICKET_TYPE_UPDATED, saved, description.toString());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public TicketTypeResponse setHidden(Long organizerId, Long ticketTypeId, boolean hidden) {
        TicketType ticketType = getOwnedTicketType(organizerId, ticketTypeId);
        ticketType.setHidden(hidden);
        TicketType saved = ticketTypeRepository.save(ticketType);
        audit(organizerId, hidden ? AuditAction.TICKET_TYPE_HIDDEN : AuditAction.TICKET_TYPE_UNHIDDEN, saved,
                "Ticket type '" + saved.getName() + "' " + (hidden ? "hidden" : "made visible"));
        return toResponse(saved);
    }

    // helpers //

    private void audit(Long actorId, AuditAction action, TicketType ticketType, String description) {
        auditService.record(actorId, action, ticketType.getId(), ticketType.getEvent().getId(), description);
    }

    private long countSold(TicketType ticketType) {
        return orderItemRepository.countSoldOrReserved(
                ticketType.getId(), OrderStatus.PAID, OrderStatus.PENDING, LocalDateTime.now());
    }

    private TicketTypeResponse toResponse(TicketType ticketType) {
        TicketTypeResponse response = ticketTypeMapper.toDto(ticketType);
        long sold = countSold(ticketType);
        response.setSold(sold);
        response.setAvailable(Math.max(ticketType.getQuantity() - sold, 0));
        return response;
    }

    private void validateSalesWindow(LocalDateTime salesStart, LocalDateTime salesEnd, Event event) {
        if (salesStart != null && salesEnd != null && !salesStart.isBefore(salesEnd)) {
            throw new BusinessRuleViolationException("Sales start must be before sales end");
        }
        if (salesStart != null && salesStart.isAfter(event.getStartTime())) {
            throw new BusinessRuleViolationException("Sales cannot start after the event starts");
        }
        if (salesEnd != null && salesEnd.isAfter(event.getStartTime())) {
            throw new BusinessRuleViolationException("Sales must end before the event starts");
        }
    }

    private void validateCapacity(Event event, int newQuantity, Long excludeTicketTypeId) {
        if (event.getCapacity() == null) {
            return; // no capacity limit configured
        }
        long allocatedElsewhere = ticketTypeRepository.sumQuantityByEventIdExcluding(event.getId(), excludeTicketTypeId);
        long remaining = event.getCapacity() - allocatedElsewhere;
        if (newQuantity > remaining) {
            throw new BusinessRuleViolationException(
                    "Quantity exceeds event capacity. Remaining unallocated capacity: " + Math.max(remaining, 0));
        }
    }

    private void ensureEventEditable(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Ticket types of a cancelled event cannot be modified");
        }
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("This event is suspended by a platform administrator");
        }
    }

    private Event getOwnedEvent(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this event");
        }
        return event;
    }

    private TicketType getOwnedTicketType(Long organizerId, Long ticketTypeId) {
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + ticketTypeId));
        if (!ticketType.getEvent().getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this ticket type");
        }
        return ticketType;
    }
}
