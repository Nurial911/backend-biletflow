package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeatHoldResponse {
    private Long holdId;
    private Long seatId;
    private LocalDateTime expirationTime;
}
