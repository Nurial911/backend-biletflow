package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventReportResponse;
import kz.edu.biletflow.backend.dtos.ReportEventRequest;
import kz.edu.biletflow.backend.dtos.ReviewEventReportRequest;
import kz.edu.biletflow.backend.entities.EventReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Users report events; platform admins review the reports (SRS 4.12).
public interface EventReportService {
    // Any signed-in user who can see the event, except its organizer. One open report per user and event.
    EventReportResponse reportEvent(Long eventId, Long reporterId, boolean isPlatformAdmin, ReportEventRequest request);

    Page<EventReportResponse> getReports(@Nullable EventReport.Status status, @Nullable Long eventId,
                                         @Nullable EventReport.Reason reason, Pageable pageable);

    EventReportResponse getReport(Long reportId);

    EventReportResponse reviewReport(Long adminId, Long reportId, ReviewEventReportRequest request);
}
