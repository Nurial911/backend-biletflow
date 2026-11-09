package kz.edu.biletflow.backend.services;

// Hands one message to the mail system. Throws if it could not be accepted.
public interface MailGateway {
    void send(OutgoingMail mail);
}
