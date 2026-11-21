package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.NotificationResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.i18n.Text;
import kz.edu.biletflow.backend.i18n.Translator;
import kz.edu.biletflow.backend.mappers.NotificationMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    private final EventRepository eventRepository;
    private final EventCalendar eventCalendar;
    private final MailGateway mailGateway;
    private final NotificationMapper notificationMapper;
    private final Translator translator;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUser(Long userId, NotificationType type, Text title, Text body, @Nullable Long orderId) {
        userRepository.findById(userId).ifPresent(user -> notificationRepository.save(build(user, type, title, body, orderId)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUsers(Collection<Long> userIds, NotificationType type, Text title, Text body) {
        saveForAll(userIds, type, title, body, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUsers(Collection<Long> userIds, NotificationType type, Text title, Text body, Long eventId) {
        saveForAll(userIds, type, title, body, eventId);
    }

    private void saveForAll(Collection<Long> userIds, NotificationType type, Text title, Text body,
                            @Nullable Long eventId) {
        notificationRepository.saveAll(userRepository.findAllById(Set.copyOf(userIds)).stream()
                .map(user -> {
                    Notification notification = build(user, type, title, body, null);
                    notification.setEventId(eventId);
                    return notification;
                })
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
                    notification.getBody(), attachments(notification)));
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

    // Built when the email is sent, from the current state: tickets of an order (ORDER_CONFIRMED) as a PDF,
    // and the event's calendar file for a confirmation, a change or a cancellation (SRS 4.11).
    private List<OutgoingMail.Attachment> attachments(Notification notification) {
        List<OutgoingMail.Attachment> attachments = new ArrayList<>();
        Locale locale = translator.localeOf(notification.getUserId());
        Long calendarEventId = notification.getEventId();
        if (notification.getOrderId() != null) {
            // Only tickets that still admit (or did): an email queued before a cancellation or refund leaves them out.
            List<Ticket> tickets = ticketRepository.findAllByOrderIdAndStatusInOrderByIdAsc(
                    notification.getOrderId(), List.of(TicketStatus.VALID, TicketStatus.USED));
            if (!tickets.isEmpty()) {
                attachments.add(new OutgoingMail.Attachment("tickets-order-" + notification.getOrderId() + ".pdf",
                        "application/pdf", ticketPdfGenerator.generate(tickets, locale)));
                if (NotificationType.ORDER_CONFIRMED.name().equals(notification.getType())) {
                    calendarEventId = tickets.getFirst().getEvent().getId();
                }
            }
        }
        if (calendarEventId != null) {
            eventRepository.findById(calendarEventId).ifPresent(event -> attachments.add(new OutgoingMail.Attachment(
                    eventCalendar.fileName(event), EventCalendar.CONTENT_TYPE, eventCalendar.ics(event, locale))));
        }
        return attachments;
    }

    // Rendered now, in the recipient's language: the row is what the user reads in the app and in the email.
    private Notification build(User user, NotificationType type, Text titleText, Text bodyText, @Nullable Long orderId) {
        Locale locale = Translator.localeOf(user);
        String title = translator.render(titleText, locale);
        String body = translator.render(bodyText, locale);
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
