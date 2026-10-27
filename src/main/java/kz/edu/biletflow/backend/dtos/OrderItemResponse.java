package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {
    private Long ticketTypeId;
    private String ticketTypeName;
    private Integer quantity;
    private BigDecimal unitPrice;
}
