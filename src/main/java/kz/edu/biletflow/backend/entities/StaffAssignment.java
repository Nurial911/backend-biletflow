package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// An EVENT_ADMIN that the organizer authorized to check attendees in at one event.
@Entity
@Table(name = "staff_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_assignments_event_user", columnNames = {"event_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_assignments_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_assignments_user"))
    private User user;
}
