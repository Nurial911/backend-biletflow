package kz.edu.biletflow.backend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull(message = "Event id cannot be null")
    private Long eventId;

    // Ticket types with a quantity: general admission, or a standing section of an assigned-seating event.
    // Optional when seatIds are given; an order needs at least one of the two.
    private List<@Valid @NotNull(message = "Item cannot be null") OrderItemRequest> items;

    // Assigned seating: the seats picked on the seat map. Each seat's section decides its ticket type and price.
    @Size(max = 50, message = "At most 50 seats per order")
    private List<@NotNull(message = "Seat id cannot be null") Long> seatIds;

    // Name for the tickets. Optional: defaults to the buyer's full name, then their email.
    @Size(max = 200, message = "Attendee name must be at most 200 characters long")
    private String attendeeName;

    // Optional. The server looks up and calculates the discount; a rejected code fails the whole order.
    @Size(max = 20, message = "Promo code must be at most 20 characters long")
    private String promoCode;
}
