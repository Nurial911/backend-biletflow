package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateTicketTypeRequest {
    private String name;
    private String description;

    @PositiveOrZero(message = "Price cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @Positive(message = "Quantity must be a positive number")
    private Integer quantity;

    private LocalDateTime salesStart;
    private LocalDateTime salesEnd;

    @Positive(message = "Max per order must be a positive number")
    private Integer maxPerOrder;
}