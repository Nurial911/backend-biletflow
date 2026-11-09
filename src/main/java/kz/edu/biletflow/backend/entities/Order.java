package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved SQL word
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // KZT, after any discount. 0 for a free order.
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // KZT taken off by the promo code. Default in the column definition so ddl-auto=update can add it.
    @Column(name = "discount_amount", nullable = false, columnDefinition = "numeric(12,2) not null default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // The campaign this order redeemed, if any. PAID or still-held orders count against its limit.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_campaign_id", foreignKey = @ForeignKey(name = "fk_orders_promo_campaign"))
    private PromoCampaign promoCampaign;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Name printed on this order's tickets. Null only on orders created before the column existed.
    @Column(name = "attendee_name", length = 200)
    private String attendeeName;

    // Inventory of a PENDING order is reserved until this moment.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_event"))
    private Event event;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
