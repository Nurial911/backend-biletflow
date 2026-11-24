package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Every error answer carries a machine-readable `code` (ErrorCode) next to the English message.
// Specific codes are also checked where their scenarios are tested (suspension, seats, promo codes, reports).
class ErrorCodeIntegrationTest extends ApiIntegrationTest {

    @Test
    void generalErrorsHaveTheirCodes() {
        Response anonymous = call(HttpMethod.GET, "/api/orders", null, null).assertStatus(401);
        assertThat(anonymous.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(anonymous.json().get("status").asInt()).isEqualTo(401);

        // Signed in, but the wrong role: 403 ACCESS_DENIED, not ACCOUNT_SUSPENDED.
        assertThat(call(HttpMethod.GET, "/api/admin/overview", register(), null).assertStatus(403).code())
                .isEqualTo("ACCESS_DENIED");

        assertThat(call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", "nobody@example.com", "password", "Wrong123!"))
                .assertStatus(401).code()).isEqualTo("BAD_CREDENTIALS");
        assertThat(call(HttpMethod.POST, "/api/auth/register", null, Map.of()).assertStatus(400).code())
                .isEqualTo("VALIDATION_FAILED");
        assertThat(call(HttpMethod.GET, "/api/events/999999999", null, null).assertStatus(404).code())
                .isEqualTo("NOT_FOUND");

        String email = "taken-" + System.nanoTime() + "@example.com";
        register(email);
        assertThat(call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("email", email, "password", PASSWORD)).assertStatus(409).code())
                .isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void ordersOutsideTheRegistrationWindowAreRefused() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        String buyer = register();

        // Only the API is used here, so this is what a client that skips the UI checks gets.
        jdbcTemplate.update("update events set registration_opening_time = ? where id = ?",
                LocalDateTime.now().plusHours(1), event);
        assertThat(call(HttpMethod.POST, "/api/orders", buyer, orderBody(event, type, 1)).assertStatus(400).code())
                .isEqualTo("REGISTRATION_NOT_OPEN");
        assertThat(call(HttpMethod.POST, "/api/orders/quote", buyer, orderBody(event, type, 1)).assertStatus(400).code())
                .isEqualTo("REGISTRATION_NOT_OPEN");

        jdbcTemplate.update("update events set registration_opening_time = null, registration_closing_time = ? where id = ?",
                LocalDateTime.now().minusHours(1), event);
        assertThat(call(HttpMethod.POST, "/api/orders", buyer, orderBody(event, type, 1)).assertStatus(400).code())
                .isEqualTo("REGISTRATION_CLOSED");

        jdbcTemplate.update("update events set registration_closing_time = null where id = ?", event);
        call(HttpMethod.POST, "/api/orders", buyer, orderBody(event, type, 1)).assertStatus(201);
    }

    @Test
    void aDraftIsHiddenFromEveryoneButItsOrganizerAndAdmins() {
        String organizer = organizer();
        long draft = draftEvent(organizer, "Hidden draft", "Draft Hall " + System.nanoTime());

        call(HttpMethod.GET, "/api/events/" + draft, organizer, null).assertStatus(200);
        call(HttpMethod.GET, "/api/events/" + draft, admin(), null).assertStatus(200);
        assertThat(call(HttpMethod.GET, "/api/events/" + draft, null, null).assertStatus(404).code()).isEqualTo("NOT_FOUND");
        call(HttpMethod.GET, "/api/events/" + draft, register(), null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + draft, organizer(), null).assertStatus(404);
    }
}
