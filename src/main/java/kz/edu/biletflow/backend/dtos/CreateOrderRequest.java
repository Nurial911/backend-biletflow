package kz.edu.biletflow.backend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull(message = "Event id cannot be null")
    private Long eventId;

    @NotEmpty(message = "Order must contain at least one item")
    private List<@Valid @NotNull(message = "Item cannot be null") OrderItemRequest> items;
}
