package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {
    Optional<SeatHold> findBySeatIdAndExpirationTimeAfter(Long seatId, LocalDateTime now);
    Optional<SeatHold> findByIdAndAttendeeUserId(Long id, Long userId);
}
