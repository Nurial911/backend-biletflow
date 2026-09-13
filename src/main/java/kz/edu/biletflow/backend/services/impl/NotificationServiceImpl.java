package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.entities.Notification;
import kz.edu.biletflow.backend.entities.NotificationType;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.NotificationRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public Notification create(Long userId, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setUser(userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found")));
        notification.setType(type);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    @Override
    public Page<Notification> list(Long userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByTimestampDesc(userId, pageable);
    }
}
