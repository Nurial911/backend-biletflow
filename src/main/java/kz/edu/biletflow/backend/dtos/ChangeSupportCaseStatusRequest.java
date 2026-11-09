package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import lombok.Data;

@Data
public class ChangeSupportCaseStatusRequest {
    @NotNull(message = "Status is required")
    private SupportCaseStatus status;
}
