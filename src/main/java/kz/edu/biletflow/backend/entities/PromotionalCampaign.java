package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotional_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class PromotionalCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "validity_start")
    private LocalDateTime validityStart;

    @Column(name = "validity_end")
    private LocalDateTime validityEnd;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "applicable_ticket_types")
    private String applicableTicketTypes;
}
