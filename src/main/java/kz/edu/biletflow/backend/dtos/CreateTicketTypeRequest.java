package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTicketTypeRequest {
    @NotBlank
    private String name;
    private String description;
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal price;
    @NotNull
    @Min(1)
    private Integer quantity;
    private LocalDateTime salesStartTime;
    private LocalDateTime salesEndTime;
    @NotNull
    @Min(1)
    private Integer limitPerOrder;
    private boolean hidden;
}
