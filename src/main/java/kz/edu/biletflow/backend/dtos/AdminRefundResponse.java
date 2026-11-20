package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One refund in the platform admin's refund monitor.
@Data
public class AdminRefundResponse {
    private Long id;
    private String reference;
    private RefundStatus status;
    private BigDecimal amount;
    private String provider;        // SIMULATED or HALYK
    private boolean simulated;
    private String reason;
    private String failureReason;
    private Long initiatedById;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long orderId;
    private Long eventId;
    private String eventTitle;
    private String buyerEmail;
}
