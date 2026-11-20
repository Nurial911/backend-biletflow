package kz.edu.biletflow.backend.dtos;

import lombok.Data;

@Data
public class AssignSectionRequest {
    // The ticket type (price category) this section sells; null takes the section off sale.
    private Long ticketTypeId;
}
