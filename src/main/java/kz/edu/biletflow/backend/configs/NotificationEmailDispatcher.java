package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Sends queued notification emails one by one, each in its own transaction.
@Component
@RequiredArgsConstructor
public class NotificationEmailDispatcher {
    private final NotificationService notificationService;

    @Scheduled(initialDelayString = "${app.mail.dispatch-delay-ms:5000}",
            fixedDelayString = "${app.mail.dispatch-delay-ms:5000}")
    public void run() {
        dispatchDue();
    }

    // Returns how many were attempted.
    public int dispatchDue() {
        List<Long> due = notificationService.findDueEmailIds();
        due.forEach(notificationService::deliverEmail);
        return due.size();
    }
}
