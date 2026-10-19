package kz.edu.biletflow.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrganizerProfileCreatedResponse {
    private OrganizerProfileResponse profile;
    private String accessToken; // new token carrying the ORGANIZER role
}
