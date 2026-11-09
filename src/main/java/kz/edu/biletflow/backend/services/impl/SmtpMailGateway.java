package kz.edu.biletflow.backend.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import kz.edu.biletflow.backend.services.MailGateway;
import kz.edu.biletflow.backend.services.OutgoingMail;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpMailGateway implements MailGateway {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    public void send(OutgoingMail mail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, mail.attachment() != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(mail.to());
            // Subjects can contain event titles, which organizers control: no header injection.
            helper.setSubject(mail.subject().replaceAll("[\\r\\n]+", " "));
            helper.setText(mail.text(), false);
            if (mail.attachment() != null) {
                helper.addAttachment(mail.attachment().filename(),
                        new ByteArrayResource(mail.attachment().content()), "application/pdf");
            }
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Could not build the email", e);
        }
    }
}
