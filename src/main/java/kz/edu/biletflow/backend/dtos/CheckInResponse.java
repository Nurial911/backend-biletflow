package kz.edu.biletflow.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {
    private CheckInResult result;
    private String message;
    private LocalDateTime checkedInAt;   // when the ticket was (first) checked in, if it was
    private TicketResponse ticket;       // null for INVALID and WRONG_EVENT
}
