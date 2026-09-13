package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "row_id", nullable = false)
    private VenueRow row;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "is_accessible", nullable = false)
    private boolean accessible;

    @Column(name = "price_category")
    private String priceCategory;

    @Version
    private long version;
}
