package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.SeatingMode;
import lombok.Data;

@Data
public class SetSeatingModeRequest {
    @NotNull(message = "Seating mode is required: GENERAL_ADMISSION or ASSIGNED")
    private SeatingMode mode;
}
