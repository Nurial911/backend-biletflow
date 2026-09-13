package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.SeatHoldResponse;

public interface SeatHoldService {
    SeatHoldResponse hold(Long userId, Long seatId);
    void release(Long userId, Long holdId);
}
