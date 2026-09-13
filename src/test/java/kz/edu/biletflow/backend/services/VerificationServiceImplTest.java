package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.repositories.CheckInRecordRepository;
import kz.edu.biletflow.backend.repositories.StaffAssignmentRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.impl.VerificationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VerificationServiceImplTest {
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final CheckInRecordRepository checkInRecordRepository = mock(CheckInRecordRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StaffAssignmentRepository staffAssignmentRepository = mock(StaffAssignmentRepository.class);
    private final QrCodeService qrCodeService = mock(QrCodeService.class);

    @Test
    void rejectsAdminWithoutAssignmentForTicketEvent() {
        User admin = new User();
        admin.setId(50L);
        Event event = new Event();
        event.setId(10L);
        Ticket ticket = new Ticket();
        ticket.setTicketIdentifier("ticket-1");
        ticket.setEvent(event);

        when(userRepository.findById(50L)).thenReturn(Optional.of(admin));
        when(ticketRepository.findByTicketIdentifier("ticket-1")).thenReturn(Optional.of(ticket));
        when(staffAssignmentRepository.existsByEventIdAndEventAdminId(10L, 50L)).thenReturn(false);

        VerificationService service = new VerificationServiceImpl(ticketRepository, checkInRecordRepository,
            userRepository, staffAssignmentRepository, qrCodeService);

        assertThrows(ForbiddenOperationException.class,
                () -> service.checkIn(50L, "ticket-1", "payload"));
        verify(qrCodeService, never()).isValid(anyString(), anyString());
        verify(checkInRecordRepository, never()).save(any());
    }
}
