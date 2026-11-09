package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.services.MailGateway;
import kz.edu.biletflow.backend.services.OutgoingMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Used while MAIL_ENABLED=false. Never logs the body or recipient: bodies can hold sign-in links.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {
    @Override
    public void send(OutgoingMail mail) {
        log.info("Email delivery is off (MAIL_ENABLED=false), dropping message: {}", mail.subject());
    }
}
