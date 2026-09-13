package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotNull
    private Long ticketTypeId;
    @NotNull
    @Min(1)
    private Integer quantity;

    private Long assignedSeatId;
}
