package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SectionKind;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeatSectionResponse {
    private Long id;
    private String key;
    private String name;
    // SEATED: pick seats (seatIds in the order). STANDING: no seats, order the ticket type with a quantity.
    private SectionKind kind;
    private int x;
    private int y;
    private int width;
    private int height;
    private int capacity;          // seats, or standing places
    private boolean onSale;        // linked to a visible ticket type
    private Long ticketTypeId;     // null when not on sale
    private String ticketTypeName;
    private BigDecimal price;
    private long available;
    private long held;             // in unpaid orders that haven't expired
    private long sold;
    private long blocked;          // seats taken off sale by the organizer
}
