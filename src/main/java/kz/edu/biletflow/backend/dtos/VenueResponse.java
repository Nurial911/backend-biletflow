package kz.edu.biletflow.backend.dtos;

import lombok.Data;

@Data
public class VenueResponse {
    private Long id;
    private String name;
    private String address;
}
