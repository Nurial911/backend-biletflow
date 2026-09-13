package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.SeatHoldResponse;
import kz.edu.biletflow.backend.entities.Attendee;
import kz.edu.biletflow.backend.entities.Seat;
import kz.edu.biletflow.backend.entities.SeatHold;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.AttendeeRepository;
import kz.edu.biletflow.backend.repositories.SeatHoldRepository;
import kz.edu.biletflow.backend.repositories.SeatRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.SeatHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SeatHoldServiceImpl implements SeatHoldService {
    private static final int HOLD_MINUTES = 10;
    private final SeatRepository seatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final AttendeeRepository attendeeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SeatHoldResponse hold(Long userId, Long seatId) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        LocalDateTime now = LocalDateTime.now();
        if (seatHoldRepository.findBySeatIdAndExpirationTimeAfter(seatId, now).isPresent()) {
            throw new BusinessRuleViolationException("Seat is already held");
        }
        Attendee attendee = attendeeRepository.findByUserId(userId).orElseGet(() -> {
            Attendee created = new Attendee();
            created.setUser(userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found")));
            return attendeeRepository.save(created);
        });
        SeatHold hold = new SeatHold();
        hold.setSeat(seat);
        hold.setAttendee(attendee);
        hold.setExpirationTime(now.plusMinutes(HOLD_MINUTES));
        return toResponse(seatHoldRepository.save(hold));
    }

    @Override
    @Transactional
    public void release(Long userId, Long holdId) {
        SeatHold hold = seatHoldRepository.findByIdAndAttendeeUserId(holdId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat hold not found"));
        seatHoldRepository.delete(hold);
    }

    private SeatHoldResponse toResponse(SeatHold hold) {
        SeatHoldResponse response = new SeatHoldResponse();
        response.setHoldId(hold.getId());
        response.setSeatId(hold.getSeat().getId());
        response.setExpirationTime(hold.getExpirationTime());
        return response;
    }
}
