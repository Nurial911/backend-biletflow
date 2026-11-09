package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaidSalesActivationResponse {
    private Long eventId;
    private String eventTitle;
    private PaidSalesStatus status;
    // Always true for now: demonstration records must never pass for real transactions (SRS 4.6).
    private boolean simulated;
    // KZT. The current fee until it has been paid, then the amount that was paid.
    private BigDecimal feeAmount;
    private String feeReference;
    private LocalDateTime feePaidAt;
    private LocalDateTime termsAcceptedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime suspendedAt;
    private String suspensionReason;
    private List<ChecklistItem> checklist;
    private boolean readyToActivate;

    @Data
    @AllArgsConstructor
    public static class ChecklistItem {
        private String key;
        private String label;
        private boolean done;
    }
}
