package kz.edu.biletflow.backend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull(message = "Event id cannot be null")
    private Long eventId;

    @NotEmpty(message = "Order must contain at least one item")
    private List<@Valid @NotNull(message = "Item cannot be null") OrderItemRequest> items;

    // Name for the tickets. Optional: defaults to the buyer's full name, then their email.
    @Size(max = 200, message = "Attendee name must be at most 200 characters long")
    private String attendeeName;
}
