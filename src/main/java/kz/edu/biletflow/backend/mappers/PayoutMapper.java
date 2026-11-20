package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.PayoutResponse;
import kz.edu.biletflow.backend.entities.Payout;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayoutMapper {
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "eventTitle", source = "event.title")
    @Mapping(target = "eventStartTime", source = "event.startTime")
    @Mapping(target = "organizerId", source = "organizer.id")
    @Mapping(target = "organizerEmail", source = "organizer.email")
    PayoutResponse toDto(Payout payout);
}
