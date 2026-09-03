package kz.edu.biletflow.backend.dtos;

import lombok.Data;

@Data
public class OrganizerProfileResponse {
    private Long id;
    private Long userId;
    private String contactInformation;
    private String verificationStatus;
    // private Long payoutAccountId;
}
