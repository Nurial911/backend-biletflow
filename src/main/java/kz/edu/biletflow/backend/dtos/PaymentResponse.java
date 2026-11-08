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
    private boolean simulated; // always true for now: show it as a demonstration, not a real transaction
    private String failureReason;
    private LocalDateTime createdAt;
}
