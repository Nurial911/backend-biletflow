package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateTicketTypeRequest {
    private String name;
    private String description;
    @DecimalMin(value = "0.00")
    private BigDecimal price;
    @Min(1)
    private Integer quantity;
    private LocalDateTime salesStartTime;
    private LocalDateTime salesEndTime;
    @Min(1)
    private Integer limitPerOrder;
    private Boolean hidden;
}
