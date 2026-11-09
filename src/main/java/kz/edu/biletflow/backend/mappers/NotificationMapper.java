package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.entities.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toDto(Notification notification);
}
