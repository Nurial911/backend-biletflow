package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SupportCaseCategory;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import kz.edu.biletflow.backend.entities.SupportCaseType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportCaseResponse {
    private Long id;
    private SupportCaseType type;
    private SupportCaseCategory category;
    private String subject;
    private SupportCaseStatus status;
    private boolean escalated;
    private Long requesterId;
    private String requesterEmail;
    // Staff only: the requester sees that someone is on it, not who.
    private Long assigneeId;
    private String assigneeEmail;
    private Long eventId;
    private String eventTitle;
    private Long orderId;
    private Long ticketId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}
