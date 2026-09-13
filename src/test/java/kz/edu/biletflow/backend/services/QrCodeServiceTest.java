package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.configs.JwtConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {
    @Test
    void signsAndRendersTicketPayload() {
        JwtConfig config = new JwtConfig();
        config.setSecret("local_development_secret_key_must_be_at_least_32_chars_long");
        QrCodeService service = new QrCodeService(config);

        String payload = service.createSignedQrPayload("ticket-123");

        assertThat(service.isValid(payload, "ticket-123")).isTrue();
        assertThat(service.isValid(payload, "ticket-other")).isFalse();
        assertThat(service.createPng(payload)).isNotEmpty();
    }
}
