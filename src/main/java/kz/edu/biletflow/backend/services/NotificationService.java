package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.entities.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import kz.edu.biletflow.backend.i18n.Text;

public interface NotificationService {
    // Both must run inside the action's transaction, so a rolled-back action notifies nobody.
    // Title and body are rendered for each recipient in their own language (User.language).
    void notifyUser(Long userId, NotificationType type, Text title, Text body, @Nullable Long orderId);

    void notifyUsers(Collection<Long> userIds, NotificationType type, Text title, Text body);

    // About a change to an event: the email carries the event's current calendar file (.ics).
    void notifyUsers(Collection<Long> userIds, NotificationType type, Text title, Text body, Long eventId);

    Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable);

    long countUnread(Long userId);

    NotificationResponse markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);

    // Used by the dispatcher.
    List<Long> findDueEmailIds();

    void deliverEmail(Long notificationId);
}
