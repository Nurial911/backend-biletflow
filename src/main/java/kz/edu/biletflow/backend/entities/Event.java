package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_organizer"))
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_venue"))
    private Venue venue;
}
