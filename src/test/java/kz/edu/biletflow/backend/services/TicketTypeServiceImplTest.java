package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.impl.TicketTypeServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TicketTypeServiceImplTest {
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);

    @Test
    void rejectsSalesWindowWhenStartIsAfterEnd() {
        Event event = new Event();
        User organizer = new User();
        organizer.setId(7L);
        event.setOrganizer(organizer);
        when(eventRepository.findById(3L)).thenReturn(Optional.of(event));

        CreateTicketTypeRequest request = new CreateTicketTypeRequest();
        request.setName("Free");
        request.setPrice(BigDecimal.ZERO);
        request.setQuantity(10);
        request.setLimitPerOrder(2);
        request.setSalesStartTime(LocalDateTime.of(2026, 10, 2, 12, 0));
        request.setSalesEndTime(LocalDateTime.of(2026, 10, 2, 11, 0));

        assertThrows(BusinessRuleViolationException.class,
                () -> service().create(7L, 3L, request));
        verify(ticketTypeRepository, never()).save(any());
    }

    private TicketTypeServiceImpl service() {
        return new TicketTypeServiceImpl(eventRepository, ticketTypeRepository, ticketRepository);
    }
}
