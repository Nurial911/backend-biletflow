package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Append-only history of check-ins and their reversals (who, when, which ticket).
@Entity
@Table(name = "check_in_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private CheckInAction action;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_check_in_records_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_check_in_records_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false, foreignKey = @ForeignKey(name = "fk_check_in_records_user"))
    private User performedBy;
}
