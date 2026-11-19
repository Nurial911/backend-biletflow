package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundResponse {
    private Long id;
    private Long orderId;
    private RefundStatus status;
    private BigDecimal amount;      // KZT, the order total the buyer paid
    private String reference;
    private String provider;        // SIMULATED or HALYK
    private boolean simulated;      // true unless a live bank returned real money
    private String reason;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
