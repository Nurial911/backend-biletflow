package kz.edu.biletflow.backend.dtos;

import lombok.Data;

// An organizer as the admin reviewing them sees it.
@Data
public class OrganizerVerificationRequestResponse {
    private Long id;
    private Long userId;
    private String email;
    private String contactInformation;
    private String verificationStatus;
}
