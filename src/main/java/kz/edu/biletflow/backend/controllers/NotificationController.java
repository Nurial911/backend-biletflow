package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// The signed-in user's own notifications, newest first.
@RequestMapping("/api/notifications")
public interface NotificationController {
    @GetMapping
    ResponseEntity<Page<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal UserPrincipal currentUser,
                                                                  Pageable pageable);

    // {"unread": 3}
    @GetMapping("/unread-count")
    ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal UserPrincipal currentUser);

    @PostMapping("/{notificationId}/read")
    ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @PathVariable Long notificationId);

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal currentUser);
}
