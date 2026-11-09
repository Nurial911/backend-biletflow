package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;

// What an order would cost right now. Reserves nothing.
@Data
public class OrderQuoteResponse {
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String promoCode;
}
