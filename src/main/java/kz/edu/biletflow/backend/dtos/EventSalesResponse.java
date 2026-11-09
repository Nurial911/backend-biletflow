package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sales of one event. Counting rules:
 * sold/revenue = PAID orders only (free tickets count as sold with 0 revenue);
 * pending = PENDING orders still holding inventory; cancelled and expired are counted separately
 * and never add to revenue. Refunds don't exist yet — once they do, they must be subtracted here.
 */
@Data
public class EventSalesResponse {
    private Long eventId;
    private String eventTitle;
    private EventStatus status;
    private boolean suspended;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;

    private long paidOrders;
    private long pendingOrders;
    private long cancelledOrders;
    private long expiredOrders;

    private long ticketsSold;
    private long ticketsReserved;
    // Gross: list prices of the tickets sold, before any promo discount. The same goes for
    // ticketTypes[].revenue and salesByDay[].revenue.
    private BigDecimal revenue;
    private BigDecimal discounts;
    private BigDecimal netRevenue;
    // Simulated figures: fees of successful payments, and net revenue minus those fees.
    private BigDecimal processingFees;
    private BigDecimal estimatedPayout;

    private List<TicketTypeSalesResponse> ticketTypes;

    // Only filled by the single-event endpoint: PAID sales grouped by payment date.
    private List<DailySalesResponse> salesByDay;
}
