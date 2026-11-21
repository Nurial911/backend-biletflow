package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.PayoutResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.NotificationType;
import kz.edu.biletflow.backend.entities.OrganizerProfile;
import kz.edu.biletflow.backend.entities.Payout;
import kz.edu.biletflow.backend.entities.PayoutAccount;
import kz.edu.biletflow.backend.entities.RefundStatus;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.i18n.Text;
import kz.edu.biletflow.backend.mappers.PayoutMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.PayoutAccountRepository;
import kz.edu.biletflow.backend.repositories.PayoutRepository;
import kz.edu.biletflow.backend.repositories.RefundRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.PayoutService;
import kz.edu.biletflow.backend.services.SalesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {
    private static final int BATCH_SIZE = 50;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final String NOTHING_TO_PAY = "Nothing to pay out: the event's paid orders were refunded.";

    private final PayoutRepository payoutRepository;
    private final EventRepository eventRepository;
    private final RefundRepository refundRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final SalesService salesService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PayoutMapper payoutMapper;
    private final TransactionTemplate transactionTemplate;

    // Placeholder: the SRS leaves the payout delay and reserve policy undecided.
    @Value("${app.payouts.delay-hours:72}")
    private long delayHours;

    // run //

    @Override
    public RunResult runPayouts() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> eventIds = transactionTemplate.execute(status -> eventRepository.findFinishedIdsWithoutPayout(
                now, now.toLocalDate().atStartOfDay(), PageRequest.of(0, BATCH_SIZE)));
        int scheduled = 0;
        for (Long eventId : eventIds) {
            // One transaction each, so one bad event can't stop the others.
            try {
                Boolean created = transactionTemplate.execute(status -> schedule(eventId));
                scheduled += Boolean.TRUE.equals(created) ? 1 : 0;
            } catch (DataIntegrityViolationException e) {
                log.info("Payout for event {} was created concurrently", eventId);
            } catch (RuntimeException e) {
                log.error("Could not schedule the payout of event {}", eventId, e);
            }
        }

        List<Long> payoutIds = transactionTemplate.execute(status -> payoutRepository.findIdsToProcess(
                Payout.Status.SCHEDULED, Payout.Status.ON_HOLD, Payout.HoldType.ADMIN, now, PageRequest.of(0, BATCH_SIZE)));
        int processed = 0;
        for (Long payoutId : payoutIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> process(lock(payoutId), null, false));
                processed++;
            } catch (RuntimeException e) {
                log.error("Could not process payout {}", payoutId, e);
            }
        }
        return new RunResult(scheduled, processed);
    }

    // reads //

    @Override
    @Transactional(readOnly = true)
    public Page<PayoutResponse> getOrganizerPayouts(Long organizerId, @Nullable Payout.Status status, Pageable pageable) {
        Specification<Payout> spec = (root, query, cb) -> {
            Predicate own = cb.equal(root.get("organizer").get("id"), organizerId);
            return status == null ? own : cb.and(own, cb.equal(root.get("status"), status));
        };
        return payoutRepository.findAll(spec, latestFirst(pageable)).map(payoutMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutResponse getEventPayout(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return payoutRepository.findByEventId(eventId).map(payoutMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This event has no payout yet: it is scheduled once the event has ended"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayoutResponse> searchPayouts(@Nullable String q, @Nullable Payout.Status status,
                                              @Nullable Long organizerId, @Nullable Long eventId, Pageable pageable) {
        String text = SearchText.normalize(q);
        Specification<Payout> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Payout, Event> event = root.join("event");
            Join<Payout, User> organizer = root.join("organizer");
            if (text != null) {
                String pattern = SearchText.containsPattern(text);
                Predicate match = cb.or(
                        cb.like(cb.lower(event.get("title")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(organizer.get("email")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("reference")), pattern, SearchText.ESCAPE));
                Long id = SearchText.asId(text);
                if (id != null) {
                    match = cb.or(match, cb.equal(root.get("id"), id), cb.equal(event.get("id"), id));
                }
                predicates.add(match);
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (organizerId != null) {
                predicates.add(cb.equal(organizer.get("id"), organizerId));
            }
            if (eventId != null) {
                predicates.add(cb.equal(event.get("id"), eventId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return payoutRepository.findAll(spec, pageable).map(payoutMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutResponse getPayout(Long payoutId) {
        return payoutMapper.toDto(payoutRepository.findWithEventById(payoutId).orElseThrow(() -> notFound(payoutId)));
    }

    // admin actions //

    @Override
    @Transactional
    public PayoutResponse holdPayout(Long adminId, Long payoutId, String reason) {
        Payout payout = lock(payoutId);
        if (payout.getStatus() == Payout.Status.PAID || payout.getStatus() == Payout.Status.CANCELLED) {
            throw new BusinessRuleViolationException("A " + payout.getStatus() + " payout cannot be put on hold");
        }
        if (payout.getHoldType() == Payout.HoldType.ADMIN) {
            throw new BusinessRuleViolationException("This payout is already held by an admin");
        }
        hold(payout, Payout.HoldType.ADMIN, reason.trim(), adminId);
        return payoutMapper.toDto(payout);
    }

    @Override
    @Transactional
    public PayoutResponse releasePayout(Long adminId, Long payoutId) {
        Payout payout = lock(payoutId);
        if (payout.getHoldType() != Payout.HoldType.ADMIN) {
            throw new BusinessRuleViolationException("Only a payout held by an admin can be released");
        }
        payout.setStatus(Payout.Status.SCHEDULED);
        payout.setHoldType(null);
        payout.setStatusReason(null);
        audit(adminId, AuditAction.PAYOUT_RELEASED, payout, "Payout hold lifted");
        // Already due: pay it right away (or hold it again for an automatic reason).
        if (!payout.getScheduledFor().isAfter(LocalDateTime.now())) {
            process(payout, adminId, false);
        }
        if (payout.getStatus() == Payout.Status.SCHEDULED) {
            notifyOrganizer(payout, Text.of("payout.rescheduled.title", title(payout)),
                    Text.of("payout.rescheduled.body", title(payout), payout.getScheduledFor(), amount(payout.getAmount())));
        }
        return payoutMapper.toDto(payout);
    }

    @Override
    @Transactional
    public PayoutResponse payNow(Long adminId, Long payoutId) {
        Payout payout = lock(payoutId);
        process(payout, adminId, true);
        return payoutMapper.toDto(payout);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureRefundAllowed(Long eventId) {
        payoutRepository.findByEventIdForUpdate(eventId)
                .filter(payout -> payout.getStatus() == Payout.Status.PAID)
                .ifPresent(payout -> {
                    throw new BusinessRuleViolationException("The organizer was already paid out for this event on "
                            + payout.getPaidAt().format(DATE_TIME) + " (" + payout.getReference() + ")."
                            + " Refunds after the payout aren't supported.");
                });
    }

    // steps //

    // Returns whether a payout row was created.
    private boolean schedule(Long eventId) {
        if (payoutRepository.findByEventId(eventId).isPresent()) {
            return false;
        }
        Event event = eventRepository.findById(eventId).orElseThrow();
        Payout payout = new Payout();
        payout.setEvent(event);
        payout.setOrganizer(event.getOrganizer());
        payout.setScheduledFor(EventPhase.effectiveEnd(event).plusHours(delayHours));
        applyFigures(payout, sales(event));

        if (payout.getAmount().signum() <= 0) {
            // Recorded anyway, so this event isn't looked at again on every run.
            payout.setStatus(Payout.Status.CANCELLED);
            payout.setStatusReason(NOTHING_TO_PAY);
            payoutRepository.save(payout);
            audit(null, AuditAction.PAYOUT_CANCELLED, payout, "No payout: " + NOTHING_TO_PAY);
            return true;
        }
        payoutRepository.save(payout);
        audit(null, AuditAction.PAYOUT_SCHEDULED, payout, "Payout of about " + kzt(payout.getAmount())
                + " scheduled for " + payout.getScheduledFor().format(DATE_TIME));
        notifyOrganizer(payout, Text.of("payout.scheduled.title", title(payout)),
                Text.of("payout.scheduled.body", title(payout), payout.getScheduledFor())
                        .and(breakdown(payout)).and("payout.scheduled.footer"));
        return true;
    }

    // Under the payout's row lock. payNow: an admin's "pay now", which ignores the date and reports what blocks it.
    private void process(Payout payout, @Nullable Long actorId, boolean payNow) {
        if (payout.getStatus() == Payout.Status.PAID || payout.getStatus() == Payout.Status.CANCELLED) {
            if (payNow) {
                throw new BusinessRuleViolationException("This payout is already " + payout.getStatus());
            }
            return;
        }
        if (payout.getHoldType() == Payout.HoldType.ADMIN) {
            if (payNow) {
                throw new BusinessRuleViolationException("This payout is held by an admin; release it first");
            }
            return;
        }
        if (!payNow && payout.getStatus() == Payout.Status.SCHEDULED
                && payout.getScheduledFor().isAfter(LocalDateTime.now())) {
            return;
        }

        Event event = payout.getEvent();
        if (event.getStatus() == EventStatus.CANCELLED) {
            cancel(payout, actorId, "The event was cancelled: its paid orders are refunded instead of paid out.",
                    "payout.cancelled.eventCancelled");
            return;
        }
        // The amount depends on it; checked again on the next run.
        if (refundRepository.existsByEventIdAndStatus(event.getId(), RefundStatus.PENDING)) {
            if (payNow) {
                throw new BusinessRuleViolationException("A refund for this event is still in progress; try again later");
            }
            return;
        }
        OrganizerProfile profile = organizerProfileRepository.findByUserId(payout.getOrganizer().getId());
        Payout.HoldType blocker = blocker(event, payout.getOrganizer(), profile);
        if (blocker != null) {
            hold(payout, blocker, holdReason(blocker), actorId);
            return;
        }

        applyFigures(payout, sales(event));
        if (payout.getAmount().signum() <= 0) {
            cancel(payout, actorId, NOTHING_TO_PAY, "payout.cancelled.nothingToPay");
            return;
        }
        PayoutAccount account = payoutAccountRepository.findById(profile.getPayoutAccountId()).orElseThrow();
        payout.setStatus(Payout.Status.PAID);
        payout.setHoldType(null);
        payout.setStatusReason(null);
        payout.setPaidAt(LocalDateTime.now());
        payout.setReference("SIM-PAYOUT-" + UUID.randomUUID());
        payout.setBankName(account.getBankName());
        payout.setIbanLast4(account.getIbanLast4());
        payout.setSimulated(true);

        audit(actorId, AuditAction.PAYOUT_PAID, payout, "Simulated payout of " + kzt(payout.getAmount()) + " to "
                + account.getBankName() + " ****" + account.getIbanLast4() + " (" + payout.getReference() + ")");
        notifyOrganizer(payout, Text.of("payout.paid.title", title(payout)),
                Text.of("payout.paid.body", title(payout), account.getBankName(), account.getIbanLast4())
                        .and(breakdown(payout)).and("payout.paid.reference", payout.getReference()));
    }

    @Nullable
    private static Payout.HoldType blocker(Event event, User organizer, @Nullable OrganizerProfile profile) {
        if (event.isSuspended()) {
            return Payout.HoldType.EVENT_SUSPENDED;
        }
        if (organizer.isSuspended()) {
            return Payout.HoldType.ORGANIZER_SUSPENDED;
        }
        if (profile == null || profile.getPayoutAccountId() == null) {
            return Payout.HoldType.NO_PAYOUT_ACCOUNT;
        }
        return null;
    }

    private static String holdReason(Payout.HoldType type) {
        return switch (type) {
            case NO_PAYOUT_ACCOUNT -> "No payout account is set up. Add one in your organizer profile and the payout"
                    + " is made automatically.";
            case EVENT_SUSPENDED -> "The event is suspended by BiletFlow. The payout continues once the suspension"
                    + " is lifted.";
            case ORGANIZER_SUSPENDED -> "The organizer's account is suspended. The payout continues once it is"
                    + " restored.";
            case ADMIN -> throw new IllegalArgumentException("Admin holds carry the admin's own reason");
        };
    }

    // Notifies only when the hold is new or its cause changed, so the scheduler doesn't repeat itself every run.
    private void hold(Payout payout, Payout.HoldType type, String reason, @Nullable Long actorId) {
        if (payout.getStatus() == Payout.Status.ON_HOLD && payout.getHoldType() == type
                && Objects.equals(payout.getStatusReason(), reason)) {
            return;
        }
        payout.setStatus(Payout.Status.ON_HOLD);
        payout.setHoldType(type);
        payout.setStatusReason(reason);
        audit(actorId, AuditAction.PAYOUT_HELD, payout, "Payout on hold: " + reason);
        // An admin's hold carries the admin's own words; automatic ones are worded in the organizer's language.
        Text reasonText = type == Payout.HoldType.ADMIN ? Text.raw(reason) : Text.of("payout.hold." + type);
        notifyOrganizer(payout, Text.of("payout.held.title", title(payout)),
                Text.of("payout.held.body", title(payout), amount(payout.getAmount()), reasonText));
    }

    // reason (English) is stored and audited; reasonKey words the organizer's notification.
    private void cancel(Payout payout, @Nullable Long actorId, String reason, String reasonKey) {
        payout.setStatus(Payout.Status.CANCELLED);
        payout.setHoldType(null);
        payout.setStatusReason(reason);
        audit(actorId, AuditAction.PAYOUT_CANCELLED, payout, "Payout cancelled: " + reason);
        notifyOrganizer(payout, Text.of("payout.cancelled.title", title(payout)), Text.of(reasonKey));
    }

    private EventSalesResponse sales(Event event) {
        return salesService.getEventsSales(List.of(event)).getFirst();
    }

    private static void applyFigures(Payout payout, EventSalesResponse sales) {
        payout.setGrossRevenue(sales.getRevenue());
        payout.setDiscounts(sales.getDiscounts());
        payout.setRefunds(sales.getRefunds());
        payout.setProcessingFees(sales.getProcessingFees());
        payout.setAmount(sales.getEstimatedPayout());
    }

    private static Text breakdown(Payout payout) {
        return Text.of("payout.breakdown", amount(payout.getGrossRevenue()), amount(payout.getDiscounts()),
                amount(payout.getRefunds()), amount(payout.getProcessingFees()), amount(payout.getAmount()));
    }

    private void audit(@Nullable Long actorId, AuditAction action, Payout payout, String description) {
        auditService.record(actorId, action, payout.getId(), payout.getEvent().getId(), description);
    }

    // Every payout message ends by saying that no real money moves.
    private void notifyOrganizer(Payout payout, Text title, Text body) {
        notificationService.notifyUser(payout.getOrganizer().getId(), NotificationType.PAYOUT_STATUS, title,
                body.and("payout.simulationNote"), null);
    }

    private Payout lock(Long payoutId) {
        return payoutRepository.findByIdForUpdate(payoutId).orElseThrow(() -> notFound(payoutId));
    }

    private static String title(Payout payout) {
        return payout.getEvent().getTitle();
    }

    private static String kzt(BigDecimal amount) {
        return amount(amount) + " KZT";
    }

    private static String amount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // Latest scheduled first unless the client asks for another order.
    private static Pageable latestFirst(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("scheduledFor"), Sort.Order.desc("id")));
    }

    private static ResourceNotFoundException notFound(Long payoutId) {
        return new ResourceNotFoundException("Payout not found with id: " + payoutId);
    }
}
