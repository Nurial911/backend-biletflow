package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.NotificationController;
import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationControllerImpl implements NotificationController {
    private final NotificationService notificationService;

    @Override
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(UserPrincipal currentUser, Pageable pageable) {
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUser.getId(), pageable));
    }

    @Override
    public ResponseEntity<Map<String, Long>> getUnreadCount(UserPrincipal currentUser) {
        return ResponseEntity.ok(Map.of("unread", notificationService.countUnread(currentUser.getId())));
    }

    @Override
    public ResponseEntity<NotificationResponse> markRead(UserPrincipal currentUser, Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(currentUser.getId(), notificationId));
    }

    @Override
    public ResponseEntity<Void> markAllRead(UserPrincipal currentUser) {
        notificationService.markAllRead(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
