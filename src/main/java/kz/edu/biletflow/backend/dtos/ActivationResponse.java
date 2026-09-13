package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.ActivationStatus;
import lombok.Data;

@Data
public class ActivationResponse {
    private Long eventId;
    private ActivationStatus status;
    private Long payoutAccountId;
}
