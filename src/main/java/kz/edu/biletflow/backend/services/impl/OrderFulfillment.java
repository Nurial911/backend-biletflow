package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.mappers.SeatLabels;
import kz.edu.biletflow.backend.repositories.PaymentRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import kz.edu.biletflow.backend.services.PlatformSettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// What happens once an order is paid, however it was paid. Callers hold the order's row lock.
@Component
@RequiredArgsConstructor
public class OrderFulfillment {
    private static final DateTimeFormatter EMAIL_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    // The rate in effect (admin setting or app.payments.processing-fee-percent); deducted from the organizer's
    // payout (SRS 3.3). Each Payment stores the fee it was charged, so a later change doesn't touch old payments.
    private final PlatformSettingsService platformSettingsService;

    public record PaymentDetails(String reference, PaymentStatus status, BigDecimal processingFee, String failureReason,
                                 String provider, String providerTransactionId, boolean simulated) {
    }

    public void markPaid(Order order, LocalDateTime now) {
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
    }

    public BigDecimal processingFee(BigDecimal amount) {
        return amount.multiply(platformSettingsService.processingFeePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Payment savePayment(Order order, PaymentDetails details) {
        Payment payment = new Payment();
        payment.setReference(details.reference());
        payment.setOrder(order);
        payment.setStatus(details.status());
        payment.setAmount(order.getTotalAmount());
        payment.setProcessingFee(details.processingFee());
        payment.setFailureReason(details.failureReason());
        payment.setProvider(details.provider());
        payment.setProviderTransactionId(details.providerTransactionId());
        payment.setSimulated(details.simulated());
        return paymentRepository.save(payment);
    }

    public void issueTickets(Order order, boolean demonstrationPayment) {
        for (OrderItem item : order.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                Ticket ticket = new Ticket();
                ticket.setCode(UUID.randomUUID().toString());
                ticket.setAttendeeName(order.getAttendeeName());
                ticket.setOrder(order);
                ticket.setEvent(order.getEvent());
                ticket.setTicketType(item.getTicketType());
                ticket.setOwner(order.getUser());
                ticket.setEventSeat(item.getEventSeat());
                ticket.setSectionName(item.getSectionName());
                ticket.setRowLabel(item.getRowLabel());
                ticket.setSeatNumber(item.getSeatNumber());
                ticketRepository.save(ticket);
            }
        }
        notifyOrderConfirmed(order, demonstrationPayment);
    }

    private void notifyOrderConfirmed(Order order, boolean demonstrationPayment) {
        Event event = order.getEvent();
        int tickets = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        String paymentNote = demonstrationPayment && order.getTotalAmount().signum() > 0
                ? "\n\nThis is a demonstration payment: no real money was charged."
                : "";
        notificationService.notifyUser(order.getUser().getId(), NotificationType.ORDER_CONFIRMED,
                "Your tickets for " + event.getTitle(),
                "Order #" + order.getId() + " for \"" + event.getTitle() + "\" is confirmed."
                        + "\n\nWhen: " + event.getStartTime().format(EMAIL_DATE_TIME) + " (Almaty time)"
                        + "\nWhere: " + event.getVenue().getName() + ", " + event.getVenue().getAddress()
                        + "\nTickets: " + tickets
                        + places(order)
                        + "\nTotal: " + order.getTotalAmount() + " KZT"
                        + "\n\nYour tickets are in your BiletFlow account, and the emailed copy of this message has them attached as a PDF,"
                        + " together with a calendar file (.ics) for the event."
                        + paymentNote,
                order.getId());
    }

    // Assigned seating: "Seats: Stalls, row A, seat 5; ..." (a standing section appears by name). Empty otherwise.
    private static String places(Order order) {
        List<String> places = order.getItems().stream()
                .map(item -> SeatLabels.describe(item.getSectionName(), item.getRowLabel(), item.getSeatNumber()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (places.isEmpty()) {
            return "";
        }
        int shown = 20;
        return "\nSeats: " + places.stream().limit(shown).collect(Collectors.joining("; "))
                + (places.size() > shown ? " and " + (places.size() - shown) + " more" : "");
    }
}
