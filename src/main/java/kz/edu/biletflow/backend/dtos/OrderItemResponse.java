package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {
    private Long ticketTypeId;
    private String ticketTypeName;
    // Assigned seating; all null for general admission. seatLabel reads e.g. "Stalls, row A, seat 5".
    private Long seatId;
    private String sectionName;
    private String rowLabel;
    private Integer seatNumber;
    private String seatLabel;
    private Integer quantity;
    private BigDecimal unitPrice;
}
