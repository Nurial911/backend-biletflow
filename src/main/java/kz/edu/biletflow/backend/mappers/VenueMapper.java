package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import kz.edu.biletflow.backend.entities.Venue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VenueMapper {
    VenueResponse toDto(Venue venue);

    @Mapping(target = "events", ignore = true)
    Venue toEntity(CreateVenueRequest createVenueRequest);
}
