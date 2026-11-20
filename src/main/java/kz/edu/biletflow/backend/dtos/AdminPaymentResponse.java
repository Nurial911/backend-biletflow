package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One payment attempt in the platform admin's payment search; FAILED ones are the payment failures to monitor.
@Data
public class AdminPaymentResponse {
    private Long id;
    private String reference;
    private PaymentStatus status;
    private BigDecimal amount;
    private BigDecimal processingFee;
    private String provider;        // SIMULATED or HALYK
    private boolean simulated;      // true unless a live bank took real money
    private String failureReason;
    private LocalDateTime createdAt;
    private Long orderId;
    private Long eventId;
    private String eventTitle;
    private String buyerEmail;
}
