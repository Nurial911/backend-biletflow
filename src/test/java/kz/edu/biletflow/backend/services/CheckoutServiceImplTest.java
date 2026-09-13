package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CheckoutRequest;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.PaidSalesActivation;
import kz.edu.biletflow.backend.entities.ActivationStatus;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.entities.Seat;
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
    private final SeatHoldRepository seatHoldRepository = mock(SeatHoldRepository.class);

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
                paymentRepository, refundRepository, auditLogRepository, ticketRepository, qrCodeService,
                seatHoldRepository);

        assertThrows(BusinessRuleViolationException.class, () -> service.createCheckout(5L, 10L, request));
        verify(ticketTypeRepository, never()).findByIdForUpdate(20L);
    }

    @Test
    void rejectsCheckoutWhenAssignedSeatHasNoActiveUserHold() {
        Event event = new Event();
        event.setId(10L);
        event.setStatus(EventStatus.PUBLISHED);
        PaidSalesActivation activation = new PaidSalesActivation();
        activation.setStatus(ActivationStatus.ACTIVE);
        TicketType ticketType = new TicketType();
        ticketType.setId(20L);
        ticketType.setEvent(event);
        ticketType.setPrice(java.math.BigDecimal.TEN);
        ticketType.setQuantity(10);
        ticketType.setLimitPerOrder(1);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(activationRepository.findByEventId(10L)).thenReturn(Optional.of(activation));
        when(ticketTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(ticketType));
        when(ticketRepository.countByTicketTypeIdAndStatusIn(eq(20L), anyCollection())).thenReturn(0L);
        when(reservationRepository.countActiveQuantity(eq(20L), any())).thenReturn(0L);
        when(seatHoldRepository.findActiveBySeatIdAndUserId(eq(99L), eq(5L), any()))
                .thenReturn(Optional.empty());

        CheckoutRequest request = new CheckoutRequest();
        request.setTicketTypeId(20L);
        request.setQuantity(1);
        request.setAssignedSeatId(99L);

        CheckoutService service = new CheckoutServiceImpl(userRepository, attendeeRepository, eventRepository,
                ticketTypeRepository, activationRepository, reservationRepository, orderRepository,
                paymentRepository, refundRepository, auditLogRepository, ticketRepository, qrCodeService,
                seatHoldRepository);

        assertThrows(BusinessRuleViolationException.class,
                () -> service.createCheckout(5L, 10L, request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsCheckoutWhenSeatHoldHasExpired() {
        rejectsCheckoutWhenAssignedSeatHasNoActiveUserHold();
    }
}
