package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A section of an event's own copy of its venue layout (SRS 4.3.1).
@Entity
@Table(name = "event_sections")
@Getter
@Setter
@NoArgsConstructor
public class EventSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_sections_event"))
    private Event event;

    // The section's key in the layout file, e.g. "STALLS" or "N2".
    @Column(name = "section_key", nullable = false, length = 20)
    private String sectionKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private SectionKind kind;

    // SEATED: number of seats; STANDING: how many people the area holds.
    @Column(name = "capacity", nullable = false)
    private int capacity;

    // The price category. Null = not on sale.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", foreignKey = @ForeignKey(name = "fk_event_sections_ticket_type"))
    private TicketType ticketType;

    // Bounding box on the map, in the layout's units.
    @Column(name = "x", nullable = false)
    private int x;

    @Column(name = "y", nullable = false)
    private int y;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
