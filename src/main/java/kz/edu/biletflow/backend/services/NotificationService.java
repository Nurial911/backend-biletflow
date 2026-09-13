package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.entities.Notification;
import kz.edu.biletflow.backend.entities.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Notification create(Long userId, NotificationType type, String message);
    Page<Notification> list(Long userId, Pageable pageable);
}
