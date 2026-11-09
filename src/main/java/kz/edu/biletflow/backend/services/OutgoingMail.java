package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;

public record OutgoingMail(String to, String subject, String text, @Nullable Attachment attachment) {
    public OutgoingMail(String to, String subject, String text) {
        this(to, subject, text, null);
    }

    public record Attachment(String filename, byte[] content) {
    }
}
