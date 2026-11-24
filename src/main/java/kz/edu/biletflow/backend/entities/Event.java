package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name = "events")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category")
    private String category;

    // The organizer's own terms, shown to buyers (SRS 4.9). Refunds themselves are always the organizer's decision.
    @Column(name = "refund_policy", length = 2000)
    private String refundPolicy;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "registration_opening_time")
    private LocalDateTime registrationOpeningTime;

    @Column(name = "registration_closing_time")
    private LocalDateTime registrationClosingTime;

    @Column(name = "capacity")
    private Integer capacity;

    // File name of the poster image in the uploads directory, e.g. "<uuid>.webp"; null = no poster.
    @Column(name = "poster_key")
    private String posterKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status")
    private VisibilityStatus visibilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    // Moderation flag set by a platform admin, independent of status so unsuspending restores it.
    // Default in the column definition so ddl-auto=update can add it to existing rows.
    @Column(name = "is_suspended", nullable = false, columnDefinition = "boolean not null default false")
    private boolean suspended = false;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    // True while paid tickets may be sold; only PaidSalesActivationServiceImpl changes it. Free tickets ignore it.
    @Column(name = "paid_sales_active", nullable = false, columnDefinition = "boolean not null default false")
    private boolean paidSalesActive = false;

    // iCalendar SEQUENCE: raised on every change of the details and on cancellation, so a newer .ics file
    // replaces the calendar entry an attendee added earlier (SRS 4.11).
    @Column(name = "calendar_sequence", nullable = false)
    private int calendarSequence = 0;

    // Changed only through SeatingService (PUT /api/events/{id}/seating), and only while the event has no orders.
    @Enumerated(EnumType.STRING)
    @Column(name = "seating_mode", nullable = false, length = 20)
    private SeatingMode seatingMode = SeatingMode.GENERAL_ADMISSION;

    // Lowest price among the event's visible (not hidden) ticket types, null when it has none. No column:
    // the database computes it on every load, so every EventResponse has it and the catalog can sort by it.
    // Read-only, and not refreshed within a transaction that changes the ticket types.
    @Formula("(select min(tt.price) from ticket_types tt where tt.event_id = id and tt.is_hidden = false)")
    @Setter(AccessLevel.NONE)
    private BigDecimal minPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_organizer"))
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_venue"))
    private Venue venue;
}
