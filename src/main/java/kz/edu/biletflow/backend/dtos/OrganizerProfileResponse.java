package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.VerificationStatus;
import lombok.Data;

@Data
public class OrganizerProfileResponse {
    private Long id;
    private Long userId;
    private String contactInformation;
    private VerificationStatus verificationStatus;
    // private Long payoutAccountId;
}
