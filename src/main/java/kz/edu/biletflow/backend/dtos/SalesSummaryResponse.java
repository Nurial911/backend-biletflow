package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalesSummaryResponse {
    private List<EventSalesResponse> events;
    private Totals totals;

    // Sums over all events in the response.
    @Data
    public static class Totals {
        private int events;
        private long paidOrders;
        private long pendingOrders;
        private long ticketsSold;
        private long ticketsReserved;
        private BigDecimal revenue;
    }
}
