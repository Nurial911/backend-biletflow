package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.NotificationMapper;
import kz.edu.biletflow.backend.repositories.NotificationRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.MailGateway;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.OutgoingMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_EMAIL_ATTEMPTS = 6; // retried after 1, 2, 4, 8 and 16 minutes
    private static final int MAX_TITLE_LENGTH = 300;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketPdfGenerator ticketPdfGenerator;
    private final MailGateway mailGateway;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUser(Long userId, NotificationType type, String title, String body, @Nullable Long orderId) {
        userRepository.findById(userId).ifPresent(user -> notificationRepository.save(build(user, type, title, body, orderId)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUsers(Collection<Long> userIds, NotificationType type, String title, String body) {
        notificationRepository.saveAll(userRepository.findAllById(Set.copyOf(userIds)).stream()
                .map(user -> build(user, type, title, body, null))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByIdDesc(userId, pageable).map(notificationMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        return notificationMapper.toDto(notification);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDueEmailIds() {
        return notificationRepository.findDueEmailIds(EmailDeliveryStatus.PENDING, LocalDateTime.now(),
                PageRequest.of(0, BATCH_SIZE));
    }

    // Transactional for the lazy ticket relations of the attachment and to store the outcome.
    @Override
    @Transactional
    public void deliverEmail(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getEmailStatus() != EmailDeliveryStatus.PENDING) {
            return;
        }
        try {
            mailGateway.send(new OutgoingMail(notification.getRecipientEmail(), notification.getTitle(),
                    notification.getBody(), ticketsAttachment(notification)));
            notification.setEmailStatus(EmailDeliveryStatus.SENT);
        } catch (Exception e) {
            recordFailure(notification, e);
        }
    }

    private void recordFailure(Notification notification, Exception cause) {
        int attempts = notification.getEmailAttempts() + 1;
        notification.setEmailAttempts(attempts);
        if (attempts >= MAX_EMAIL_ATTEMPTS) {
            notification.setEmailStatus(EmailDeliveryStatus.FAILED);
            log.error("Giving up on email for notification {}", notification.getId(), cause);
        } else {
            notification.setNextAttemptAt(LocalDateTime.now().plusMinutes(1L << (attempts - 1)));
            log.warn("Could not send email for notification {} (attempt {}): {}",
                    notification.getId(), attempts, cause.getMessage());
        }
    }

    @Nullable
    private OutgoingMail.Attachment ticketsAttachment(Notification notification) {
        if (notification.getOrderId() == null) {
            return null;
        }
        List<Ticket> tickets = ticketRepository.findAllByOrderIdAndStatusNotOrderByIdAsc(
                notification.getOrderId(), TicketStatus.CANCELLED);
        return tickets.isEmpty() ? null : new OutgoingMail.Attachment(
                "tickets-order-" + notification.getOrderId() + ".pdf", ticketPdfGenerator.generate(tickets));
    }

    private Notification build(User user, NotificationType type, String title, String body, @Nullable Long orderId) {
        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setRecipientEmail(user.getEmail());
        notification.setType(type.name());
        notification.setTitle(title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title);
        notification.setBody(body);
        notification.setOrderId(orderId);
        notification.setNextAttemptAt(LocalDateTime.now());
        return notification;
    }
}
