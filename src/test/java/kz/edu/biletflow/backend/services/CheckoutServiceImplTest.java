package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CheckoutRequest;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.PaidSalesActivation;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.impl.CheckoutServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CheckoutServiceImplTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final PaidSalesActivationRepository activationRepository = mock(PaidSalesActivationRepository.class);
    private final InventoryReservationRepository reservationRepository = mock(InventoryReservationRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final QrCodeService qrCodeService = mock(QrCodeService.class);

    @Test
    void rejectsPaidCheckoutUntilSalesActivationIsActive() {
        Event event = new Event();
        event.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        PaidSalesActivation activation = new PaidSalesActivation();
        activationRepository.findByEventId(10L);
        when(activationRepository.findByEventId(10L)).thenReturn(Optional.of(activation));

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketTypeId(20L);
        request.setQuantity(1);

        CheckoutService service = new CheckoutServiceImpl(userRepository, attendeeRepository, eventRepository,
                ticketTypeRepository, activationRepository, reservationRepository, orderRepository,
                paymentRepository, refundRepository, auditLogRepository, ticketRepository, qrCodeService);

        assertThrows(BusinessRuleViolationException.class, () -> service.createCheckout(5L, 10L, request));
        verify(ticketTypeRepository, never()).findByIdForUpdate(20L);
    }
}
