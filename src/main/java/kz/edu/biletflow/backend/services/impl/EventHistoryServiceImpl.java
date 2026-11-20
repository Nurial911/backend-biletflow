package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.EventHistoryEntryResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.AuditLog;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.EventMapper;
import kz.edu.biletflow.backend.repositories.AuditLogRepository;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.EventHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventHistoryServiceImpl implements EventHistoryService {
    // Bounds used when the caller leaves the date range open.
    private static final LocalDateTime EARLIEST = LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final LocalDateTime LATEST = LocalDate.of(3000, 1, 1).atStartOfDay();
    private static final String SUPPORT_TEAM_NAME = "BiletFlow Support";

    private final EventRepository eventRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getOrganizerEvents(Long organizerId, @Nullable EventPhase phase, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        // Same rules as EventPhase.of, written as a query so the filter works across pages.
        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizer").get("id"), organizerId));
            if (phase != null) {
                Path<EventStatus> status = root.get("status");
                Path<LocalDateTime> start = root.get("startTime");
                Path<LocalDateTime> end = root.get("endTime");
                if (phase == EventPhase.CANCELLED) {
                    predicates.add(cb.equal(status, EventStatus.CANCELLED));
                } else {
                    predicates.add(cb.notEqual(status, EventStatus.CANCELLED));
                    switch (phase) {
                        case UPCOMING -> predicates.add(cb.greaterThan(start, now));
                        case ACTIVE -> {
                            predicates.add(cb.lessThanOrEqualTo(start, now));
                            predicates.add(cb.or(
                                    cb.and(cb.isNotNull(end), cb.greaterThan(end, now)),
                                    cb.and(cb.isNull(end), cb.greaterThanOrEqualTo(start, todayStart))));
                        }
                        case COMPLETED -> {
                            predicates.add(cb.lessThanOrEqualTo(start, now));
                            predicates.add(cb.or(
                                    cb.and(cb.isNotNull(end), cb.lessThanOrEqualTo(end, now)),
                                    cb.and(cb.isNull(end), cb.lessThan(start, todayStart))));
                        }
                        default -> throw new IllegalStateException("Unhandled phase " + phase);
                    }
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return eventRepository.findAll(spec, pageable).map(eventMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventHistoryEntryResponse> getEventHistory(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                                           @Nullable LocalDate from, @Nullable LocalDate to,
                                                           @Nullable List<AuditAction> actions, @Nullable String entityType,
                                                           Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        // Organizer of the event or a platform admin. Check-in staff and everyone else get 404.
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleViolationException("'from' must not be after 'to'.");
        }

        Set<String> actionNames = selectActions(actions, entityType);
        // The order is fixed by the query; a client-supplied sort would be ignored or break it.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (actionNames.isEmpty()) {
            return Page.empty(page);
        }

        Page<AuditLog> entries = auditLogRepository.findEventHistory(eventId,
                from != null ? from.atStartOfDay() : EARLIEST,
                to != null ? to.plusDays(1).atStartOfDay() : LATEST,
                actionNames, page);

        Set<Long> actorIds = entries.stream().map(AuditLog::getActorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return entries.map(entry -> toResponse(entry, actors.get(entry.getActorId()), requesterId, isPlatformAdmin));
    }

    // Actions matching both filters; no filter means every action.
    private static Set<String> selectActions(@Nullable List<AuditAction> actions, @Nullable String entityType) {
        String type = entityType == null || entityType.isBlank() ? null : entityType.trim();
        if (type != null && Arrays.stream(AuditAction.values()).noneMatch(a -> a.getEntityType().equalsIgnoreCase(type))) {
            throw new BusinessRuleViolationException("Unknown entity type: " + type);
        }
        return Arrays.stream(AuditAction.values())
                .filter(a -> actions == null || actions.isEmpty() || actions.contains(a))
                .filter(a -> type == null || a.getEntityType().equalsIgnoreCase(type))
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    private static EventHistoryEntryResponse toResponse(AuditLog entry, @Nullable User actor,
                                                        Long requesterId, boolean isPlatformAdmin) {
        User.Role role = actor != null ? actor.getRole() : null;
        boolean hideActor = !isPlatformAdmin && role == User.Role.PLATFORM_ADMIN && !requesterId.equals(entry.getActorId());
        if (hideActor) {
            return new EventHistoryEntryResponse(entry.getId(), entry.getCreatedAt(), null, SUPPORT_TEAM_NAME, null,
                    role, entry.getAction(), entry.getEntityType(), entry.getEntityId(), entry.getDescription());
        }
        String name = actor != null && actor.getFullName() != null && !actor.getFullName().isBlank()
                ? actor.getFullName()
                : entry.getActorEmail();
        return new EventHistoryEntryResponse(entry.getId(), entry.getCreatedAt(), entry.getActorId(), name,
                entry.getActorEmail(), role, entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getDescription());
    }
}
