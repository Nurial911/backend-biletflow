package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// One message of a case's thread. Messages are never edited or deleted.
@Entity
@Table(name = "support_messages", indexes = @Index(name = "idx_support_messages_case", columnList = "case_id, id"))
@Getter
@Setter
@NoArgsConstructor
public class SupportMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_support_messages_case"))
    private SupportCase supportCase;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    // A team name for staff, so attendees don't see staff members' personal addresses.
    @Column(name = "author_name", nullable = false, updatable = false, length = 200)
    private String authorName;

    @Column(name = "from_staff", nullable = false, updatable = false)
    private boolean fromStaff;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
