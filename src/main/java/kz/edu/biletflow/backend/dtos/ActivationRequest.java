package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivationRequest {
    @AssertTrue
    private boolean eventDetailsConfirmed;
    @AssertTrue
    private boolean payoutAccountConfirmed;
    @AssertTrue
    private boolean termsAccepted;
    @NotBlank
    private String payoutAccountDetails;
}
