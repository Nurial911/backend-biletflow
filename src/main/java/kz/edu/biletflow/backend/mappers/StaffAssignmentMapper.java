package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.StaffResponse;
import kz.edu.biletflow.backend.entities.StaffAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffAssignmentMapper {
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "assignedAt", source = "createdAt")
    StaffResponse toDto(StaffAssignment assignment);
}
