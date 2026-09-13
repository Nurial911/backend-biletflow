package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.SupportCaseType;
import lombok.Data;

@Data
public class CreateSupportCaseRequest {
    @NotNull
    private SupportCaseType caseType;
    private Long eventId;
    private Long orderId;
    private Long ticketId;
    private String issueCategory;
}
