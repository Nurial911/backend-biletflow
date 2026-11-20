package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.Payout;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A simulated payout of one event's sales (simulated is always true: no money moves).
// amount = grossRevenue - discounts - refunds - processingFees; estimated until status is PAID.
@Data
public class PayoutResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private LocalDateTime eventStartTime;
    private Long organizerId;
    private String organizerEmail;

    private Payout.Status status;
    private Payout.HoldType holdType;   // set while ON_HOLD
    private String statusReason;        // why it is ON_HOLD or CANCELLED

    private BigDecimal grossRevenue;
    private BigDecimal discounts;
    private BigDecimal refunds;
    private BigDecimal processingFees;
    private BigDecimal amount;

    private LocalDateTime scheduledFor;
    private LocalDateTime paidAt;
    private String reference;           // SIM-PAYOUT-..., once PAID
    private String bankName;            // destination at the time of payment
    private String ibanLast4;
    private boolean simulated;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
