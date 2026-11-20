package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.entities.Ticket;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketMapper {
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "eventTitle", source = "event.title")
    @Mapping(target = "ticketTypeId", source = "ticketType.id")
    @Mapping(target = "ticketTypeName", source = "ticketType.name")
    @Mapping(target = "attendeeEmail", source = "owner.email")
    @Mapping(target = "seatId", source = "eventSeat.id")
    @Mapping(target = "seatLabel", expression = "java(kz.edu.biletflow.backend.mappers.SeatLabels.describe(ticket.getSectionName(), ticket.getRowLabel(), ticket.getSeatNumber()))")
    TicketResponse toDto(Ticket ticket);
}
