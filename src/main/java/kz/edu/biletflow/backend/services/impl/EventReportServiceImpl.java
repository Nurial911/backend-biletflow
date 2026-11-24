package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.EventReportResponse;
import kz.edu.biletflow.backend.dtos.ReportEventRequest;
import kz.edu.biletflow.backend.dtos.ReviewEventReportRequest;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventReport;
import kz.edu.biletflow.backend.entities.NotificationType;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ErrorCode;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.i18n.Text;
import kz.edu.biletflow.backend.repositories.EventReportRepository;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.EventReportService;
import kz.edu.biletflow.backend.services.EventService;
import kz.edu.biletflow.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventReportServiceImpl implements EventReportService {
    private final EventReportRepository reportRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public EventReportResponse reportEvent(Long eventId, Long reporterId, boolean isPlatformAdmin,
                                           ReportEventRequest request) {
        // Same visibility as the event page: an event the user can't see is 404, so its existence isn't revealed.
        eventService.getEventById(eventId, reporterId, isPlatformAdmin);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (event.getOrganizer().getId().equals(reporterId)) {
            throw new BusinessRuleViolationException("You cannot report your own event");
        }
        if (reportRepository.existsByEventIdAndReporterIdAndStatus(eventId, reporterId, EventReport.Status.OPEN)) {
            throw new DuplicateResourceException(ErrorCode.EVENT_ALREADY_REPORTED, "You have already reported this event; the report is waiting for review");
        }

        EventReport report = new EventReport();
        report.setEvent(event);
        report.setReporter(userRepository.getReferenceById(reporterId));
        report.setReason(request.getReason());
        report.setMessage(request.getMessage() != null && !request.getMessage().isBlank()
                ? request.getMessage().trim() : null);
        return toDto(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventReportResponse> getReports(@Nullable EventReport.Status status, @Nullable Long eventId,
                                                @Nullable EventReport.Reason reason, Pageable pageable) {
        Specification<EventReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return reportRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public EventReportResponse getReport(Long reportId) {
        return toDto(getReportEntity(reportId));
    }

    @Override
    @Transactional
    public EventReportResponse reviewReport(Long adminId, Long reportId, ReviewEventReportRequest request) {
        if (request.getOutcome() == EventReport.Status.OPEN) {
            throw new BusinessRuleViolationException("Outcome must be DISMISSED or ACTIONED");
        }
        EventReport report = getReportEntity(reportId);
        if (report.getStatus() != EventReport.Status.OPEN) {
            throw new BusinessRuleViolationException("This report has already been reviewed");
        }
        String note = request.getNote() != null && !request.getNote().isBlank() ? request.getNote().trim() : null;
        report.setStatus(request.getOutcome());
        report.setReviewedById(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setResolutionNote(note);
        EventReport saved = reportRepository.save(report);

        Event event = saved.getEvent();
        String outcome = saved.getStatus() == EventReport.Status.ACTIONED ? "actioned" : "dismissed";
        // No event id: the organizer's timeline must not show reports or who made them.
        auditService.record(adminId, AuditAction.EVENT_REPORT_REVIEWED, saved.getId(), null,
                "Report about event #" + event.getId() + " (" + saved.getReason() + ") " + outcome
                        + (note != null ? ": " + note : ""));
        notificationService.notifyUser(saved.getReporter().getId(), NotificationType.EVENT_REPORT_REVIEWED,
                Text.of("eventReport.title"),
                Text.of(saved.getStatus() == EventReport.Status.ACTIONED ? "eventReport.actioned" : "eventReport.dismissed",
                        event.getTitle()),
                null);
        return toDto(saved);
    }

    private EventReport getReportEntity(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));
    }

    private EventReportResponse toDto(EventReport report) {
        Event event = report.getEvent();
        EventReportResponse dto = new EventReportResponse();
        dto.setId(report.getId());
        dto.setEventId(event.getId());
        dto.setEventTitle(event.getTitle());
        dto.setEventStatus(event.getStatus());
        dto.setEventSuspended(event.isSuspended());
        dto.setReporterId(report.getReporter().getId());
        dto.setReporterEmail(report.getReporter().getEmail());
        dto.setReason(report.getReason());
        dto.setMessage(report.getMessage());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setReviewedById(report.getReviewedById());
        dto.setReviewedAt(report.getReviewedAt());
        dto.setResolutionNote(report.getResolutionNote());
        return dto;
    }
}
