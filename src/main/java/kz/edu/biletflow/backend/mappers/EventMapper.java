package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {
    @Mapping(target = "organizerId", source = "organizer.id")
    EventResponse toDto(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venue", ignore = true) // set explicitly in the service after lookup
    @Mapping(target = "organizer", ignore = true) // set explicitly in the service after lookup
    Event toEntity(CreateEventRequest request);
}
