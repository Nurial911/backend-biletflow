package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponse {
    private Long id;
    private String reference;
    private PaymentStatus status;
    private BigDecimal amount;
    private BigDecimal processingFee;
    private boolean simulated; // true unless a live bank took real money: show it as a demonstration then
    private String provider; // SIMULATED or HALYK
    private String failureReason;
    private LocalDateTime createdAt;
}
