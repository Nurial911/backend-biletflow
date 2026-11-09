package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import kz.edu.biletflow.backend.dtos.OpenSupportCaseRequest;
import kz.edu.biletflow.backend.dtos.SupportCaseDetailResponse;
import kz.edu.biletflow.backend.dtos.SupportCaseResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.SupportMapper;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.EventService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Asynchronous support conversations (SRS 4.13). Who can see a case:
 * its requester; platform admins; and, on ATTENDEE cases, that event's organizer and assigned staff.
 * Anyone else gets 404, so case ids can't be probed.
 */
@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {
    private static final int NOTIFICATION_EXCERPT_LENGTH = 500;

    private final SupportCaseRepository caseRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SupportMapper supportMapper;

    @Override
    @Transactional
    public SupportCaseDetailResponse openCase(Long userId, boolean isPlatformAdmin, OpenSupportCaseRequest request) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        SupportCase supportCase = new SupportCase();
        supportCase.setType(request.getType() != null ? request.getType() : SupportCaseType.ATTENDEE);
        supportCase.setCategory(request.getCategory());
        supportCase.setSubject(request.getSubject().trim());
        supportCase.setRequester(requester);
        supportCase.setRequesterEmail(requester.getEmail());
        supportCase.setUpdatedAt(LocalDateTime.now());
        if (supportCase.getType() == SupportCaseType.PLATFORM) {
            attachPlatformContext(supportCase, requester, request);
        } else {
            attachAttendeeContext(supportCase, userId, isPlatformAdmin, request);
        }
        SupportCase saved = caseRepository.save(supportCase);

        saveMessage(saved, requester, false, request.getMessage());
        notifyUsers(handlerRecipients(saved), userId, NotificationType.SUPPORT_MESSAGE,
                "New support case #" + saved.getId() + ": " + saved.getSubject(), excerpt(request.getMessage()));
        return toDetail(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupportCaseResponse> getCases(Long userId, boolean isPlatformAdmin, @Nullable SupportCaseStatus status,
                                              @Nullable Boolean escalated, Pageable pageable) {
        Specification<SupportCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (escalated != null) {
                predicates.add(cb.equal(root.get("escalated"), escalated));
            }
            if (!isPlatformAdmin) {
                var event = root.join("event", JoinType.LEFT);
                Subquery<Long> assignment = query.subquery(Long.class);
                var staff = assignment.from(StaffAssignment.class);
                assignment.select(staff.get("id")).where(
                        cb.equal(staff.get("event"), event),
                        cb.equal(staff.get("user").get("id"), userId));
                predicates.add(cb.or(
                        cb.equal(root.get("requester").get("id"), userId),
                        cb.and(cb.equal(root.get("type"), SupportCaseType.ATTENDEE),
                                cb.or(cb.equal(event.get("organizer").get("id"), userId), cb.exists(assignment)))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRepository.findAll(spec, pageable)
                .map(supportCase -> toDto(supportCase, isHandler(supportCase, userId, isPlatformAdmin)));
    }

    @Override
    @Transactional(readOnly = true)
    public SupportCaseDetailResponse getCase(Long caseId, Long userId, boolean isPlatformAdmin) {
        SupportCase supportCase = getAccessibleCase(caseId, userId, isPlatformAdmin);
        return toDetail(supportCase, isHandler(supportCase, userId, isPlatformAdmin));
    }

    @Override
    @Transactional
    public SupportCaseDetailResponse addMessage(Long caseId, Long userId, boolean isPlatformAdmin, String body) {
        SupportCase supportCase = getAccessibleCase(caseId, userId, isPlatformAdmin);
        boolean requester = isRequester(supportCase, userId);
        boolean handler = isHandler(supportCase, userId, isPlatformAdmin);

        if (supportCase.getStatus() == SupportCaseStatus.RESOLVED) {
            if (!requester) {
                throw new BusinessRuleViolationException("This case is resolved; change its status to reply");
            }
            // The requester writing again means the problem isn't over.
            supportCase.setStatus(SupportCaseStatus.OPEN);
            supportCase.setResolvedAt(null);
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        saveMessage(supportCase, author, handler && !requester, body);
        supportCase.setUpdatedAt(LocalDateTime.now());

        Set<Long> recipients = requester ? handlerRecipients(supportCase) : Set.of(supportCase.getRequester().getId());
        notifyUsers(recipients, userId, NotificationType.SUPPORT_MESSAGE,
                "New reply on support case #" + caseId + ": " + supportCase.getSubject(), excerpt(body));
        return toDetail(supportCase, handler);
    }

    @Override
    @Transactional
    public SupportCaseDetailResponse assign(Long caseId, Long userId, boolean isPlatformAdmin, String assigneeEmail) {
        SupportCase supportCase = getHandledCase(caseId, userId, isPlatformAdmin);
        User assignee = userRepository.findByEmailIgnoreCase(assigneeEmail.trim())
                .filter(user -> isHandler(supportCase, user.getId(), user.getRole() == User.Role.PLATFORM_ADMIN))
                .orElseThrow(() -> new BusinessRuleViolationException("That user cannot handle this case"));

        supportCase.setAssignee(assignee);
        supportCase.setAssigneeEmail(assignee.getEmail());
        supportCase.setUpdatedAt(LocalDateTime.now());
        audit(userId, AuditAction.SUPPORT_CASE_ASSIGNED, supportCase, "Support case #" + caseId + " assigned");
        notifyUsers(Set.of(assignee.getId()), userId, NotificationType.SUPPORT_ASSIGNED,
                "Support case #" + caseId + " was assigned to you", supportCase.getSubject());
        return toDetail(supportCase, true);
    }

    @Override
    @Transactional
    public SupportCaseDetailResponse changeStatus(Long caseId, Long userId, boolean isPlatformAdmin, SupportCaseStatus status) {
        SupportCase supportCase = getHandledCase(caseId, userId, isPlatformAdmin);
        SupportCaseStatus previous = supportCase.getStatus();
        if (previous != status) {
            LocalDateTime now = LocalDateTime.now();
            supportCase.setStatus(status);
            supportCase.setResolvedAt(status == SupportCaseStatus.RESOLVED ? now : null);
            supportCase.setUpdatedAt(now);
            audit(userId, AuditAction.SUPPORT_CASE_STATUS_CHANGED, supportCase,
                    "Support case #" + caseId + " status " + previous + " -> " + status);
            notifyUsers(Set.of(supportCase.getRequester().getId()), userId, NotificationType.SUPPORT_STATUS,
                    "Support case #" + caseId + " is now " + status,
                    "The status of your support case \"" + supportCase.getSubject() + "\" changed to " + status + ".");
        }
        return toDetail(supportCase, true);
    }

    @Override
    @Transactional
    public SupportCaseDetailResponse escalate(Long caseId, Long userId, boolean isPlatformAdmin) {
        SupportCase supportCase = getHandledCase(caseId, userId, isPlatformAdmin);
        if (supportCase.isEscalated()) {
            throw new BusinessRuleViolationException("This case is already escalated");
        }
        supportCase.setEscalated(true);
        supportCase.setUpdatedAt(LocalDateTime.now());
        audit(userId, AuditAction.SUPPORT_CASE_ESCALATED, supportCase, "Support case #" + caseId + " escalated");
        notifyUsers(userRepository.findIdsByRole(User.Role.PLATFORM_ADMIN), userId, NotificationType.SUPPORT_ASSIGNED,
                "Support case #" + caseId + " was escalated", supportCase.getSubject());
        return toDetail(supportCase, true);
    }

    // helpers //

    private void attachPlatformContext(SupportCase supportCase, User requester, OpenSupportCaseRequest request) {
        if (requester.getRole() != User.Role.ORGANIZER) {
            throw new ForbiddenOperationException("Only organizers can open platform support cases");
        }
        if (request.getOrderId() != null || request.getTicketId() != null) {
            throw new BusinessRuleViolationException("A platform support case can only refer to an event");
        }
        if (request.getEventId() != null) {
            Event event = eventRepository.findById(request.getEventId())
                    .filter(e -> e.getOrganizer().getId().equals(requester.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));
            supportCase.setEvent(event);
        }
    }

    // A ticket implies its order and event, and an order implies its event. Things that aren't
    // the requester's own are reported as missing.
    private void attachAttendeeContext(SupportCase supportCase, Long userId, boolean isPlatformAdmin, OpenSupportCaseRequest request) {
        Ticket ticket = null;
        Order order = null;
        Event event;
        if (request.getTicketId() != null) {
            ticket = ticketRepository.findById(request.getTicketId())
                    .filter(t -> t.getOwner().getId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + request.getTicketId()));
            order = ticket.getOrder();
            event = ticket.getEvent();
        } else if (request.getOrderId() != null) {
            order = orderRepository.findById(request.getOrderId())
                    .filter(o -> o.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));
            event = order.getEvent();
        } else if (request.getEventId() != null) {
            eventService.getEventById(request.getEventId(), userId, isPlatformAdmin); // 404 for an event they may not see
            event = eventRepository.getReferenceById(request.getEventId());
        } else {
            throw new BusinessRuleViolationException("A support case must refer to an event, an order or a ticket");
        }
        if (request.getOrderId() != null && !request.getOrderId().equals(order.getId())) {
            throw new BusinessRuleViolationException("The ticket does not belong to that order");
        }
        if (request.getEventId() != null && !request.getEventId().equals(event.getId())) {
            throw new BusinessRuleViolationException("The order or ticket does not belong to that event");
        }
        supportCase.setTicket(ticket);
        supportCase.setOrder(order);
        supportCase.setEvent(event);
    }

    private SupportCase getAccessibleCase(Long caseId, Long userId, boolean isPlatformAdmin) {
        SupportCase supportCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Support case not found with id: " + caseId));
        if (!isRequester(supportCase, userId) && !isHandler(supportCase, userId, isPlatformAdmin)) {
            throw new ResourceNotFoundException("Support case not found with id: " + caseId);
        }
        return supportCase;
    }

    private SupportCase getHandledCase(Long caseId, Long userId, boolean isPlatformAdmin) {
        SupportCase supportCase = getAccessibleCase(caseId, userId, isPlatformAdmin);
        if (!isHandler(supportCase, userId, isPlatformAdmin)) {
            throw new ForbiddenOperationException("Only support staff can do this");
        }
        return supportCase;
    }

    private boolean isRequester(SupportCase supportCase, Long userId) {
        return supportCase.getRequester().getId().equals(userId);
    }

    private boolean isHandler(SupportCase supportCase, Long userId, boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return true;
        }
        if (supportCase.getType() != SupportCaseType.ATTENDEE) {
            return false;
        }
        Event event = supportCase.getEvent();
        return event.getOrganizer().getId().equals(userId)
                || staffAssignmentRepository.existsByEventIdAndUserId(event.getId(), userId);
    }

    // Whoever should hear about something the requester did.
    private Set<Long> handlerRecipients(SupportCase supportCase) {
        if (supportCase.getAssignee() != null) {
            return Set.of(supportCase.getAssignee().getId());
        }
        return supportCase.getType() == SupportCaseType.ATTENDEE
                ? Set.of(supportCase.getEvent().getOrganizer().getId())
                : new LinkedHashSet<>(userRepository.findIdsByRole(User.Role.PLATFORM_ADMIN));
    }

    private void notifyUsers(Iterable<Long> recipients, Long actorId, NotificationType type, String title, String body) {
        Set<Long> others = new LinkedHashSet<>();
        recipients.forEach(others::add);
        others.remove(actorId); // nobody needs telling about their own action
        if (!others.isEmpty()) {
            notificationService.notifyUsers(others, type, title, body);
        }
    }

    private void saveMessage(SupportCase supportCase, User author, boolean fromStaff, String body) {
        SupportMessage message = new SupportMessage();
        message.setSupportCase(supportCase);
        message.setAuthorId(author.getId());
        message.setFromStaff(fromStaff);
        message.setAuthorName(fromStaff ? staffDisplayName(author) : displayName(author));
        message.setBody(body.trim());
        messageRepository.save(message);
    }

    private String staffDisplayName(User author) {
        return author.getRole() == User.Role.PLATFORM_ADMIN ? "BiletFlow Support" : "Event organizer team";
    }

    private String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail();
    }

    private void audit(Long actorId, AuditAction action, SupportCase supportCase, String description) {
        Long eventId = supportCase.getEvent() != null ? supportCase.getEvent().getId() : null;
        auditService.record(actorId, action, supportCase.getId(), eventId, description);
    }

    private String excerpt(String body) {
        String text = body.trim();
        return text.length() > NOTIFICATION_EXCERPT_LENGTH
                ? text.substring(0, NOTIFICATION_EXCERPT_LENGTH) + "..."
                : text;
    }

    private SupportCaseResponse toDto(SupportCase supportCase, boolean handler) {
        SupportCaseResponse dto = supportMapper.toDto(supportCase);
        if (!handler) {
            dto.setAssigneeId(null);
            dto.setAssigneeEmail(null);
        }
        return dto;
    }

    private SupportCaseDetailResponse toDetail(SupportCase supportCase, boolean handler) {
        return new SupportCaseDetailResponse(toDto(supportCase, handler),
                messageRepository.findAllBySupportCaseIdOrderByIdAsc(supportCase.getId()).stream()
                        .map(supportMapper::toDto)
                        .toList());
    }
}
