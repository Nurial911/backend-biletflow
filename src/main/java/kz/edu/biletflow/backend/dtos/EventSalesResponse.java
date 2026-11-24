package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sales of one event. Counting rules:
 * ticketsSold = tickets of PAID orders (free tickets count, with 0 revenue); ticketsRefunded = tickets of
 * REFUNDED orders, which are back on sale; pending = PENDING orders still holding inventory; cancelled and
 * expired are counted separately and never add to revenue.
 * Money: revenue is gross (every sale that happened, refunded ones included) and
 * netRevenue = revenue - discounts - refunds, i.e. what buyers paid and kept paid.
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
    private long refundedOrders;

    private long ticketsSold;
    private long ticketsReserved;
    private long ticketsRefunded;
    // Against the event capacity (SRS 4.15); both null when the event has no capacity.
    // ticketsRemaining = capacity - sold - reserved, never below 0; percentSold = sold / capacity, one decimal.
    // ticketsRemaining includes places that aren't on sale (blocked seats, sections without a ticket type,
    // hidden ticket types): occupancy.available is what buyers can still get.
    private Long ticketsRemaining;
    private BigDecimal percentSold;
    // Every place of the event split into sold / reserved / available / hidden / blocked / not on sale.
    private OccupancyResponse occupancy;
    // Gross: list prices of PAID and REFUNDED orders, before any promo discount. The same goes for
    // ticketTypes[].revenue and salesByDay[].revenue.
    private BigDecimal revenue;
    private BigDecimal discounts;  // promo discounts on PAID and REFUNDED orders
    private BigDecimal refunds;    // money returned by successful refunds
    private BigDecimal netRevenue;
    // Simulated figures: fees of successful payments of PAID orders (none on refunded orders),
    // and net revenue minus those fees.
    private BigDecimal processingFees;
    private BigDecimal estimatedPayout;

    private List<TicketTypeSalesResponse> ticketTypes;

    // Only filled by the single-event endpoint: sales grouped by payment date, including ones refunded later.
    private List<DailySalesResponse> salesByDay;
}
