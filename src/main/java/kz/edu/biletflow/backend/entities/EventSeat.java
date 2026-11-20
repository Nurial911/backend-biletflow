package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One seat of an event. Whether it is held or sold isn't stored here: it follows from the orders that hold it,
// so an expired, cancelled or refunded order frees its seats by itself. Rows are created in bulk (SeatingServiceImpl).
@Entity
@Table(name = "event_seats")
@Getter
@Setter
@NoArgsConstructor
public class EventSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_seats_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_seats_section"))
    private EventSection section;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    // Seat centre on the map, in the layout's units.
    @Column(name = "x", nullable = false)
    private int x;

    @Column(name = "y", nullable = false)
    private int y;

    @Column(name = "accessible", nullable = false)
    private boolean accessible;

    // Taken off sale by the organizer.
    @Column(name = "blocked", nullable = false)
    private boolean blocked;
}
