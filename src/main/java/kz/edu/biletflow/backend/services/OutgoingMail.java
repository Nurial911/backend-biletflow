package kz.edu.biletflow.backend.services;

import java.util.List;

public record OutgoingMail(String to, String subject, String text, List<Attachment> attachments) {
    public OutgoingMail {
        attachments = List.copyOf(attachments);
    }

    public OutgoingMail(String to, String subject, String text) {
        this(to, subject, text, List.of());
    }

    public record Attachment(String filename, String contentType, byte[] content) {
    }
}
