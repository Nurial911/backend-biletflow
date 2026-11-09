package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.MailGateway;
import kz.edu.biletflow.backend.services.OutgoingMail;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

// Keeps every email in memory, so tests can read links and attachments.
public class CapturingMailGateway implements MailGateway {
    private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failNext = new AtomicBoolean();

    @Override
    public void send(OutgoingMail mail) {
        if (failNext.getAndSet(false)) {
            throw new IllegalStateException("Simulated mail server outage");
        }
        sent.add(mail);
    }

    public void failNextSend() {
        failNext.set(true);
    }

    public List<OutgoingMail> sentTo(String address) {
        return sent.stream().filter(mail -> mail.to().equals(address)).toList();
    }

    @TestConfiguration
    static class Config {
        // Real SMTP tests set app.mail.enabled=true and use the real gateway instead.
        @Bean
        @Primary
        @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
        CapturingMailGateway capturingMailGateway() {
            return new CapturingMailGateway();
        }
    }
}
