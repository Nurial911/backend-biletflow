package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.entities.TicketType;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TicketTypeMapper {
    @Mapping(target = "eventId", source = "event.id")
    TicketTypeResponse toDto(TicketType ticketType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)     // set in the service after the ownership check
    @Mapping(target = "hidden", ignore = true)    // new ticket types start visible
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "maxPerOrder", source = "maxPerOrder", defaultValue = "10")
    TicketType toEntity(CreateTicketTypeRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)     // a ticket type never moves between events
    @Mapping(target = "hidden", ignore = true)    // changed through hide/unhide endpoints
    @Mapping(target = "createdAt", ignore = true)
    void updateTicketType(UpdateTicketTypeRequest request, @MappingTarget TicketType ticketType);
}