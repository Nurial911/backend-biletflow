package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.entities.Seat;
import kz.edu.biletflow.backend.entities.SeatHold;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.AttendeeRepository;
import kz.edu.biletflow.backend.repositories.SeatHoldRepository;
import kz.edu.biletflow.backend.repositories.SeatRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.impl.SeatHoldServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SeatHoldServiceImplTest {
    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final SeatHoldRepository seatHoldRepository = mock(SeatHoldRepository.class);
    private final AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void rejectsSeatWhenAnotherActiveHoldExists() {
        when(seatRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(new Seat()));
        when(seatHoldRepository.findBySeatIdAndExpirationTimeAfter(eq(4L), any(LocalDateTime.class)))
                .thenReturn(Optional.of(new SeatHold()));

        SeatHoldService service = new SeatHoldServiceImpl(seatRepository, seatHoldRepository,
                attendeeRepository, userRepository);

        assertThrows(BusinessRuleViolationException.class, () -> service.hold(9L, 4L));
        verify(attendeeRepository, never()).save(any());
    }
}
