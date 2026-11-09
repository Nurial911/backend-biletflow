package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.services.MailGateway;
import kz.edu.biletflow.backend.services.OutgoingMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Off the request thread, so response time doesn't reveal whether an address has an account.
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailSender {
    private final MailGateway mailGateway;

    @Async
    public void send(OutgoingMail mail) {
        try {
            mailGateway.send(mail);
        } catch (Exception e) {
            // Nothing stored to retry from on purpose; the user can ask for a new link.
            log.warn("Could not send account email: {}", e.getMessage());
        }
    }
}
