package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import kz.edu.biletflow.backend.entities.SupportCaseType;
import lombok.Data;

@Data
public class SupportCaseResponse {
    private Long id;
    private SupportCaseType caseType;
    private SupportCaseStatus status;
    private Long eventId;
    private Long orderId;
    private Long ticketId;
    private String issueCategory;
}
