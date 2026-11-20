package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.Payment;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.PaymentRepository;
import kz.edu.biletflow.backend.services.AdminReportService;
import kz.edu.biletflow.backend.services.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;

    private final EventRepository eventRepository;
    private final PaymentRepository paymentRepository;
    private final SalesService salesService;

    @Override
    public CsvReport eventsReport(@Nullable LocalDate from, @Nullable LocalDate to) {
        DateRange range = range(from, to);
        List<Event> events = eventRepository.findAllStartingBetween(range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay());
        List<EventSalesResponse> sales = salesService.getEventsSales(events);

        CsvWriter csv = new CsvWriter().row("event_id", "title", "organizer_email", "status", "suspended",
                "start_time", "end_time", "capacity", "paid_orders", "pending_orders", "refunded_orders",
                "tickets_sold", "tickets_reserved", "tickets_refunded", "revenue_kzt", "discounts_kzt", "refunds_kzt",
                "net_revenue_kzt", "processing_fees_kzt", "estimated_payout_kzt");
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            EventSalesResponse s = sales.get(i);
            csv.row(event.getId(), event.getTitle(), event.getOrganizer().getEmail(), event.getStatus().name(),
                    event.isSuspended(), event.getStartTime(), event.getEndTime(), event.getCapacity(),
                    s.getPaidOrders(), s.getPendingOrders(), s.getRefundedOrders(),
                    s.getTicketsSold(), s.getTicketsReserved(), s.getTicketsRefunded(), s.getRevenue(),
                    s.getDiscounts(), s.getRefunds(), s.getNetRevenue(), s.getProcessingFees(), s.getEstimatedPayout());
        }
        return new CsvReport(fileName("events", range), csv.toBytes());
    }

    @Override
    public CsvReport paymentsReport(@Nullable LocalDate from, @Nullable LocalDate to) {
        DateRange range = range(from, to);
        List<Payment> payments = paymentRepository.findAllCreatedBetween(range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay());

        // "simulated" stays in every row: demonstration payments must never pass for real money (SRS 4.6).
        CsvWriter csv = new CsvWriter().row("payment_id", "created_at", "reference", "status", "provider", "simulated",
                "amount_kzt", "processing_fee_kzt", "failure_reason", "order_id", "order_status", "event_id",
                "event_title", "buyer_email");
        for (Payment payment : payments) {
            Order order = payment.getOrder();
            csv.row(payment.getId(), payment.getCreatedAt(), payment.getReference(), payment.getStatus().name(),
                    payment.getProvider(), payment.isSimulated(), payment.getAmount(), payment.getProcessingFee(),
                    payment.getFailureReason(), order.getId(), order.getStatus().name(), order.getEvent().getId(),
                    order.getEvent().getTitle(), order.getUser().getEmail());
        }
        return new CsvReport(fileName("payments", range), csv.toBytes());
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private static DateRange range(@Nullable LocalDate from, @Nullable LocalDate to) {
        LocalDate end = to != null ? to : (from != null ? from.plusDays(DEFAULT_DAYS - 1) : LocalDate.now());
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new BusinessRuleViolationException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessRuleViolationException("A report can cover at most " + MAX_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    private static String fileName(String report, DateRange range) {
        return "biletflow-" + report + "-" + range.from() + "_" + range.to() + ".csv";
    }
}
