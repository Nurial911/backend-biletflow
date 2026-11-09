package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// The real SMTP path: Spring's mail sender talking to a real mail server (Mailpit), attachment included.
@TestPropertySource(properties = "app.mail.enabled=true")
class SmtpDeliveryIntegrationTest extends ApiIntegrationTest {

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.27")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("MAIL_HOST", MAILPIT::getHost);
        registry.add("MAIL_PORT", () -> MAILPIT.getMappedPort(1025));
    }

    @Test
    void orderConfirmationReachesTheMailServerWithItsPdfAndCyrillicSubject() {
        // Leftovers from other test classes would only slow this down.
        jdbcTemplate.update("UPDATE notifications SET email_status = 'SENT' WHERE email_status = 'PENDING'");
        String organizer = organizer();
        long event = publishedEvent(organizer, "Концерт", "Зал");
        long type = ticketType(organizer, event, 0, 10);
        String email = "smtp-" + UUID.randomUUID() + "@test.kz";
        String attendee = register(email);

        await().atMost(Duration.ofSeconds(15)).until(() -> messagesTo(email).size() == 1); // the verification link

        createOrder(attendee, event, type, 2);
        deliverQueuedEmails();

        await().atMost(Duration.ofSeconds(15)).until(() -> messagesTo(email).size() == 2);
        JsonNode confirmation = messagesTo(email).stream()
                .filter(m -> m.get("Subject").asString().equals("Your tickets for Концерт"))
                .findFirst().orElseThrow();
        assertThat(confirmation.get("Attachments").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_status FROM notifications WHERE recipient_email = ?", String.class, email)).isEqualTo("SENT");
    }

    private List<JsonNode> messagesTo(String email) {
        String body = RestClient.create()
                .get()
                .uri("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/search?query=to:\"{email}\"", email)
                .retrieve()
                .body(String.class);
        return list(JSON.readTree(body).get("messages"));
    }
}
