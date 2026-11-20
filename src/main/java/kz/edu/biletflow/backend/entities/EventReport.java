package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A user's report that an event breaks the rules (SRS 4.12 "review reported events").
// Reviewing it doesn't change the event: an admin who agrees suspends the event separately.
@Entity
@Table(name = "event_reports")
@Getter
@Setter
@NoArgsConstructor
public class EventReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_reports_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_reports_reporter"))
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private Reason reason;

    @Column(name = "message", length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    public enum Reason {
        FRAUD, MISLEADING, INAPPROPRIATE, SPAM, OTHER
    }

    public enum Status {
        OPEN,
        DISMISSED, // nothing wrong found
        ACTIONED   // the admin acted on it, e.g. suspended the event
    }
}
