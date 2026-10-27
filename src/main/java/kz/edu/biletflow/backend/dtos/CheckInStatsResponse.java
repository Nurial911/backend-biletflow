package kz.edu.biletflow.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInStatsResponse {
    private Long eventId;
    private long registered;    // tickets that grant entry (valid + already used)
    private long checkedIn;
    private long notCheckedIn;
}
