package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.entities.Event;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EventMapper {
    @Mapping(target = "organizerId", source = "organizer.id")
    EventResponse toDto(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true) // set explicitly in the service after lookup
    @Mapping(target = "venue", ignore = true) // set explicitly in the service after lookup
    @Mapping(target = "status", ignore = true) // new events default to DRAFT; state changes go through publish/unpublish/cancel
    Event toEntity(CreateEventRequest request);


    // Only overwrite fields that were actually provided in the patch; a null field
    // in the request means "leave this alone", not "clear it" - same fix as UserMapper.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true) // ownership never changes via a plain edit
    @Mapping(target = "venue", ignore = true) // set explicitly in the service after lookup, only if venueId was provided
    @Mapping(target = "status", ignore = true) // state changes go through publish/unpublish/cancel, not a plain edit
    void updateEvent(UpdateEventRequest request, @MappingTarget Event event);

}
