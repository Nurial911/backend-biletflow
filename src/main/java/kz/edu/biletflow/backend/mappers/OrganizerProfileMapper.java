package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import kz.edu.biletflow.backend.entities.OrganizerProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganizerProfileMapper {
    @Mapping(target = "userId", source = "user.id")
    OrganizerProfileResponse toDto(OrganizerProfile organizerProfile);
}
