package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.entities.Event;
import org.mapstruct.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Mapper(componentModel = "spring")
public interface EventMapper {
    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "posterUrl", expression = "java(posterUrl(event))")
    EventResponse toDto(Event event);

    // Absolute URL so a frontend on another origin can use it directly; ?v= changes on every
    // upload so browsers never show a stale cached poster.
    default String posterUrl(Event event) {
        if (event.getPosterKey() == null) {
            return null;
        }
        UriComponentsBuilder base = RequestContextHolder.getRequestAttributes() != null
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromPath(""); // outside a web request (e.g. schedulers): relative URL
        return base.path("/api/events/{id}/poster")
                .queryParam("v", event.getPosterKey())
                .buildAndExpand(event.getId())
                .toUriString();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true) // set explicitly in the service after lookup
    @Mapping(target = "venue", ignore = true) // set explicitly in the service after lookup
    @Mapping(target = "status", ignore = true) // new events default to DRAFT; state changes go through publish/unpublish/cancel
    @Mapping(target = "posterKey", ignore = true) // poster changes only through /poster endpoints
    Event toEntity(CreateEventRequest request);


    // Only overwrite fields that were actually provided in the patch; a null field
    // in the request means "leave this alone", not "clear it" - same fix as UserMapper.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", ignore = true) // ownership never changes via a plain edit
    @Mapping(target = "venue", ignore = true) // set explicitly in the service after lookup, only if venueId was provided
    @Mapping(target = "status", ignore = true) // state changes go through publish/unpublish/cancel, not a plain edit
    @Mapping(target = "posterKey", ignore = true) // poster changes only through /poster endpoints
    void updateEvent(UpdateEventRequest request, @MappingTarget Event event);

}
