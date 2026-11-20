package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BlockSeatsRequest {
    @NotEmpty(message = "List at least one seat")
    @Size(max = 1000, message = "At most 1000 seats per request")
    private List<@NotNull(message = "Seat id cannot be null") Long> seatIds;

    // true takes the seats off sale, false puts them back.
    @NotNull(message = "blocked is required")
    private Boolean blocked;
}
