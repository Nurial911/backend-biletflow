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

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "registration_opening_time")
    private LocalDateTime registrationOpeningTime;

    @Column(name = "registration_closing_time")
    private LocalDateTime registrationClosingTime;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "visibility_status")
    private String visibilityStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_organizer"))
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_venue"))
    private Venue venue;
}
