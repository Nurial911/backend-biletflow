package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.PaidSalesActivationResponse;
import kz.edu.biletflow.backend.dtos.PaidSalesActivationResponse.ChecklistItem;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.PaidSalesActivationRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.PaidSalesActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-event activation of paid sales (SRS 3.2, 4.5). Every step is a simulation; nothing is charged.
 * The Event row is locked during changes, and Event.paidSalesActive is the flag the checkout reads.
 */
@Service
@RequiredArgsConstructor
public class PaidSalesActivationServiceImpl implements PaidSalesActivationService {
    private final PaidSalesActivationRepository activationRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // Placeholder amount: the SRS leaves it undecided (section 12).
    @Value("${app.payments.activation-fee-kzt:5000}")
    private BigDecimal activationFee;

    @Override
    @Transactional(readOnly = true)
    public PaidSalesActivationResponse getActivation(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        // Public at the filter level (GET /api/events/**), so anyone else must not learn the event exists.
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return toDto(event, activationRepository.findByEventId(eventId).orElseGet(PaidSalesActivation::new));
    }

    @Override
    @Transactional
    public PaidSalesActivationResponse payFee(Long organizerId, Long eventId) {
        Event event = getOwnedEventForUpdate(organizerId, eventId);
        if (!hasPaidTicketType(event)) {
            throw new BusinessRuleViolationException("Add a paid ticket type before paying the activation fee");
        }
        PaidSalesActivation activation = getOrCreate(event);
        if (activation.getFeePaidAt() != null) {
            throw new BusinessRuleViolationException("The activation fee has already been paid for this event");
        }
        activation.setFeeAmount(activationFee);
        activation.setFeeReference("SIM-" + UUID.randomUUID());
        activation.setFeePaidAt(LocalDateTime.now());
        PaidSalesActivation saved = activationRepository.save(activation);
        auditService.record(organizerId, AuditAction.ACTIVATION_FEE_PAID, saved.getId(), eventId,
                "Simulated activation fee of " + activationFee + " KZT paid");
        return toDto(event, saved);
    }

    @Override
    @Transactional
    public PaidSalesActivationResponse acceptTerms(Long organizerId, Long eventId) {
        Event event = getOwnedEventForUpdate(organizerId, eventId);
        PaidSalesActivation activation = getOrCreate(event);
        if (activation.getTermsAcceptedAt() != null) {
            throw new BusinessRuleViolationException("The paid-event terms have already been accepted");
        }
        activation.setTermsAcceptedAt(LocalDateTime.now());
        PaidSalesActivation saved = activationRepository.save(activation);
        auditService.record(organizerId, AuditAction.ACTIVATION_TERMS_ACCEPTED, saved.getId(), eventId,
                "Paid-event terms accepted");
        return toDto(event, saved);
    }

    @Override
    @Transactional
    public PaidSalesActivationResponse activate(Long organizerId, Long eventId) {
        Event event = getOwnedEventForUpdate(organizerId, eventId);
        PaidSalesActivation activation = getOrCreate(event);
        if (activation.getStatus() != PaidSalesStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException("Paid sales for this event are already " + activation.getStatus());
        }
        String missing = checklist(event, activation).stream()
                .filter(item -> !item.isDone())
                .map(ChecklistItem::getLabel)
                .collect(Collectors.joining("; "));
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException("Paid sales can't be activated yet. Still to do: " + missing);
        }
        activation.setStatus(PaidSalesStatus.ACTIVE);
        activation.setActivatedAt(LocalDateTime.now());
        event.setPaidSalesActive(true);
        PaidSalesActivation saved = activationRepository.save(activation);
        auditService.record(organizerId, AuditAction.PAID_SALES_ACTIVATED, saved.getId(), eventId, "Paid sales activated");
        return toDto(event, saved);
    }

    @Override
    @Transactional
    public PaidSalesActivationResponse suspend(Long adminId, Long eventId, @Nullable String reason) {
        Event event = getEventForUpdate(eventId);
        PaidSalesActivation activation = activationRepository.findByEventId(eventId)
                .filter(a -> a.getStatus() == PaidSalesStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleViolationException("Paid sales are not active for this event"));
        String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        activation.setStatus(PaidSalesStatus.SUSPENDED);
        activation.setSuspendedAt(LocalDateTime.now());
        activation.setSuspensionReason(cleanReason);
        event.setPaidSalesActive(false);
        String detail = cleanReason != null ? ": " + cleanReason : "";
        auditService.record(adminId, AuditAction.PAID_SALES_SUSPENDED, activation.getId(), eventId,
                "Paid sales suspended" + detail);
        notificationService.notifyUser(event.getOrganizer().getId(), NotificationType.PAID_SALES_STATUS,
                "Paid sales suspended for " + event.getTitle(),
                "A platform administrator suspended paid sales for \"" + event.getTitle() + "\"" + detail
                        + ". Free tickets and tickets already sold are not affected.", null);
        return toDto(event, activation);
    }

    @Override
    @Transactional
    public PaidSalesActivationResponse unsuspend(Long adminId, Long eventId) {
        Event event = getEventForUpdate(eventId);
        PaidSalesActivation activation = activationRepository.findByEventId(eventId)
                .filter(a -> a.getStatus() == PaidSalesStatus.SUSPENDED)
                .orElseThrow(() -> new BusinessRuleViolationException("Paid sales are not suspended for this event"));
        activation.setStatus(PaidSalesStatus.ACTIVE);
        activation.setSuspendedAt(null);
        activation.setSuspensionReason(null);
        event.setPaidSalesActive(true);
        auditService.record(adminId, AuditAction.PAID_SALES_UNSUSPENDED, activation.getId(), eventId,
                "Paid sales suspension lifted");
        notificationService.notifyUser(event.getOrganizer().getId(), NotificationType.PAID_SALES_STATUS,
                "Paid sales restored for " + event.getTitle(),
                "A platform administrator lifted the suspension of paid sales for \"" + event.getTitle() + "\".", null);
        return toDto(event, activation);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaidSalesActivationResponse> getActivations(@Nullable PaidSalesStatus status, Pageable pageable) {
        Page<PaidSalesActivation> page = status != null
                ? activationRepository.findAllByStatus(status, pageable)
                : activationRepository.findAll(pageable);
        return page.map(activation -> toDto(activation.getEvent(), activation));
    }

    // helpers //

    private Event getEventForUpdate(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
    }

    private Event getOwnedEventForUpdate(Long organizerId, Long eventId) {
        Event event = getEventForUpdate(eventId);
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this event");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be activated for paid sales");
        }
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("This event is suspended by a platform administrator");
        }
        return event;
    }

    private PaidSalesActivation getOrCreate(Event event) {
        return activationRepository.findByEventId(event.getId()).orElseGet(() -> {
            PaidSalesActivation activation = new PaidSalesActivation();
            activation.setEvent(event);
            return activation;
        });
    }

    private boolean hasPaidTicketType(Event event) {
        return ticketTypeRepository.existsByEventIdAndHiddenFalseAndPriceGreaterThan(event.getId(), BigDecimal.ZERO);
    }

    private List<ChecklistItem> checklist(Event event, PaidSalesActivation activation) {
        OrganizerProfile profile = organizerProfileRepository.findByUserId(event.getOrganizer().getId());
        boolean verified = profile != null && profile.getVerificationStatus() == OrganizerVerificationStatus.VERIFIED;
        boolean payoutAccount = profile != null && profile.getPayoutAccountId() != null;
        return List.of(
                new ChecklistItem("PAID_TICKET_TYPE", "Create at least one paid ticket type", hasPaidTicketType(event)),
                new ChecklistItem("IDENTITY_VERIFIED", "Complete identity verification", verified),
                new ChecklistItem("PAYOUT_ACCOUNT", "Register a payout account", payoutAccount),
                new ChecklistItem("FEE_PAID", "Pay the activation fee", activation.getFeePaidAt() != null),
                new ChecklistItem("TERMS_ACCEPTED", "Accept the paid-event terms", activation.getTermsAcceptedAt() != null));
    }

    private PaidSalesActivationResponse toDto(Event event, PaidSalesActivation activation) {
        PaidSalesActivationResponse dto = new PaidSalesActivationResponse();
        dto.setEventId(event.getId());
        dto.setEventTitle(event.getTitle());
        dto.setStatus(activation.getStatus());
        dto.setSimulated(activation.isSimulated());
        dto.setFeeAmount(activation.getFeeAmount() != null ? activation.getFeeAmount() : activationFee);
        dto.setFeeReference(activation.getFeeReference());
        dto.setFeePaidAt(activation.getFeePaidAt());
        dto.setTermsAcceptedAt(activation.getTermsAcceptedAt());
        dto.setActivatedAt(activation.getActivatedAt());
        dto.setSuspendedAt(activation.getSuspendedAt());
        dto.setSuspensionReason(activation.getSuspensionReason());
        List<ChecklistItem> checklist = checklist(event, activation);
        dto.setChecklist(checklist);
        dto.setReadyToActivate(activation.getStatus() == PaidSalesStatus.IN_PROGRESS
                && checklist.stream().allMatch(ChecklistItem::isDone));
        return dto;
    }
}
