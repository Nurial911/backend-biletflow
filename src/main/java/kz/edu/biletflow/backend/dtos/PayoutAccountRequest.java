package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PayoutAccountRequest {
    @NotBlank(message = "Bank name is required")
    @Size(max = 120, message = "Bank name must be at most 120 characters long")
    private String bankName;

    @NotBlank(message = "Account holder is required")
    @Size(max = 200, message = "Account holder must be at most 200 characters long")
    private String accountHolder;

    // Checked for shape, then reduced to its last 4 characters. The full number is never stored.
    @NotBlank(message = "IBAN is required")
    @Pattern(regexp = "^KZ[0-9]{2}[0-9A-Z]{16}$", message = "IBAN must be a Kazakhstan IBAN: KZ followed by 18 characters")
    private String iban;
}
