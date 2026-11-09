package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.SupportCaseResponse;
import kz.edu.biletflow.backend.dtos.SupportMessageResponse;
import kz.edu.biletflow.backend.entities.SupportCase;
import kz.edu.biletflow.backend.entities.SupportMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupportMapper {
    @Mapping(target = "requesterId", source = "requester.id")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "eventTitle", source = "event.title")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "ticketId", source = "ticket.id")
    SupportCaseResponse toDto(SupportCase supportCase);

    SupportMessageResponse toDto(SupportMessage message);
}
