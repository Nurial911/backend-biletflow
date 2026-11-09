package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.configs.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The demo data must keep working with the checkout rules: it is what the project is shown with.
@TestPropertySource(properties = "app.seed.enabled=true")
class SeedDataIntegrationTest extends ApiIntegrationTest {

    @Test
    void seededConcertCanBeBoughtWithItsPromoCodeAndTheFestivalShowsTheChecklist() {
        long concert = eventId("Spring Jazz Night");
        long festival = eventId("Kazakh Folk Festival");
        long standard = jdbcTemplate.queryForObject(
                "SELECT id FROM ticket_types WHERE event_id = ? AND name = 'Standard'", Long.class, concert);
        String attendee = login("attendee@biletflow.kz", DataSeeder.DEMO_PASSWORD);

        Map<String, Object> body = new HashMap<>(orderBody(concert, standard, 1));
        body.put("promoCode", "JAZZ2026");
        JsonNode order = call(HttpMethod.POST, "/api/orders", attendee, body).assertStatus(201).json();

        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("4000");
        pay(attendee, order.get("id").asLong()).assertStatus(200);

        String organizer1 = login("organizer1@biletflow.kz", DataSeeder.DEMO_PASSWORD);
        assertThat(call(HttpMethod.GET, "/api/events/" + concert + "/activation", organizer1, null)
                .assertStatus(200).json().get("status").asString()).isEqualTo("ACTIVE");

        String organizer2 = login("organizer2@biletflow.kz", DataSeeder.DEMO_PASSWORD);
        JsonNode checklist = call(HttpMethod.GET, "/api/events/" + festival + "/activation", organizer2, null)
                .assertStatus(200).json();
        assertThat(checklist.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(checklist.get("readyToActivate").asBoolean()).isFalse();
    }

    private long eventId(String title) {
        return jdbcTemplate.queryForObject("SELECT id FROM events WHERE title = ?", Long.class, title);
    }
}
