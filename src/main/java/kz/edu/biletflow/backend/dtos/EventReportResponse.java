package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventReport;
import kz.edu.biletflow.backend.entities.EventStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventReportResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private EventStatus eventStatus;
    private boolean eventSuspended;
    private Long reporterId;
    private String reporterEmail;
    private EventReport.Reason reason;
    private String message;
    private EventReport.Status status;
    private LocalDateTime createdAt;
    private Long reviewedById;
    private LocalDateTime reviewedAt;
    private String resolutionNote;
}
