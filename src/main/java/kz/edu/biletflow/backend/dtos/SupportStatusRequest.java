package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import lombok.Data;

@Data
public class SupportStatusRequest {
    @NotNull
    private SupportCaseStatus status;
}
