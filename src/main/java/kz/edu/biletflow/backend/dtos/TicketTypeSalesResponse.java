package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TicketTypeSalesResponse {
    private Long ticketTypeId;
    private String name;
    private BigDecimal price;     // current price; revenue uses the price snapshot of each order
    private boolean hidden;
    private int quantity;         // total inventory
    private long sold;            // tickets in PAID orders
    private long reserved;        // tickets held by PENDING orders that haven't expired yet
    private long available;       // quantity - sold - reserved, never below 0
    private BigDecimal revenue;   // PAID orders only, KZT
}
