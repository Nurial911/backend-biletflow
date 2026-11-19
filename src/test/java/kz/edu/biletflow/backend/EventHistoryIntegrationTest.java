package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Organizer event history (SRS 4.16): events by phase and each event's activity timeline.
class EventHistoryIntegrationTest extends ApiIntegrationTest {

    @Test
    void timelineListsTheEventsActionsNewestFirstWithWhoDidThem() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        String buyer = register();
        long order = createOrder(buyer, event, type, 1).get("id").asLong();
        pay(buyer, order).assertStatus(200);
        JsonNode ticket = tickets(buyer).get(0);
        scan(organizer, event, ticket.get("code").asString());
        call(HttpMethod.POST, "/api/check-in/events/" + event + "/tickets/" + ticket.get("id").asLong() + "/undo",
                organizer, null).assertStatus(200);
        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);

        List<JsonNode> entries = history(organizer, event, "");

        assertThat(entries).extracting(e -> e.get("action").asString()).containsExactly(
                "EVENT_CANCELLED", "TICKET_CHECK_IN_UNDONE", "TICKET_CHECKED_IN", "PAYMENT_SUCCEEDED",
                "TICKET_TYPE_CREATED", "EVENT_PUBLISHED", "EVENT_CREATED");
        JsonNode cancelled = entries.get(0);
        assertThat(cancelled.get("actorEmail").asString()).isEqualTo(emailOf(organizer));
        assertThat(cancelled.get("actorRole").asString()).isEqualTo("ORGANIZER");
        assertThat(cancelled.get("entityType").asString()).isEqualTo("EVENT");
        assertThat(cancelled.get("entityId").asLong()).isEqualTo(event);
        assertThat(cancelled.get("timestamp").isNull()).isFalse();
        assertThat(cancelled.get("description").asString()).isNotBlank();
        JsonNode payment = entries.get(3);
        assertThat(payment.get("actorEmail").asString()).isEqualTo(emailOf(buyer));
        assertThat(payment.get("entityType").asString()).isEqualTo("PAYMENT");
    }

    @Test
    void historyCanBeFilteredByActivityTypeAndDateRange() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        ticketType(organizer, event, 0, 10);
        String today = LocalDate.now().toString();

        assertThat(history(organizer, event, "?entityType=TICKET_TYPE"))
                .extracting(e -> e.get("action").asString()).containsExactly("TICKET_TYPE_CREATED");
        assertThat(history(organizer, event, "?action=EVENT_CREATED&action=EVENT_PUBLISHED"))
                .extracting(e -> e.get("action").asString()).containsExactly("EVENT_PUBLISHED", "EVENT_CREATED");
        assertThat(history(organizer, event, "?entityType=EVENT&action=TICKET_TYPE_CREATED")).isEmpty();
        assertThat(history(organizer, event, "?from=" + today + "&to=" + today)).hasSize(3);
        assertThat(history(organizer, event, "?from=" + LocalDate.now().plusDays(1))).isEmpty();
        assertThat(history(organizer, event, "?to=" + LocalDate.now().minusDays(1))).isEmpty();

        String base = "/api/organizer/events/" + event + "/history";
        call(HttpMethod.GET, base + "?from=" + today + "&to=" + LocalDate.now().minusDays(1), organizer, null).assertStatus(400);
        call(HttpMethod.GET, base + "?action=NOT_AN_ACTION", organizer, null).assertStatus(400);
        call(HttpMethod.GET, base + "?entityType=NOTHING", organizer, null).assertStatus(400);
        call(HttpMethod.GET, base + "?from=yesterday", organizer, null).assertStatus(400);
    }

    @Test
    void historyIsPaged() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        for (int i = 0; i < 3; i++) {
            ticketType(organizer, event, 0, 1);
        }

        JsonNode page = call(HttpMethod.GET, "/api/organizer/events/" + event + "/history?size=2&page=1&sort=action,asc",
                organizer, null).assertStatus(200).json();

        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        // The client's sort is ignored: still newest first.
        assertThat(list(page.get("content"))).extracting(e -> e.get("action").asString())
                .containsExactly("TICKET_TYPE_CREATED", "EVENT_PUBLISHED");
    }

    @Test
    void onlyTheEventsOrganizerOrAPlatformAdminCanReadTheHistory() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String staff = eventAdmin();
        call(HttpMethod.POST, "/api/events/" + event + "/staff", organizer, Map.of("email", emailOf(staff)))
                .assertStatus(201);
        String path = "/api/organizer/events/" + event + "/history";

        call(HttpMethod.GET, path, organizer(), null).assertStatus(404); // another organizer
        call(HttpMethod.GET, path, staff, null).assertStatus(403);       // check-in staff: no organizer role
        call(HttpMethod.GET, path, register(), null).assertStatus(403);
        call(HttpMethod.GET, path, null, null).assertStatus(401);
        call(HttpMethod.GET, "/api/organizer/events/999999999/history", organizer, null).assertStatus(404);

        assertThat(history(admin(), event, "")).isNotEmpty();
    }

    @Test
    void platformAdminsAppearToOrganizersAsBiletFlowSupport() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String admin = admin();
        call(HttpMethod.POST, "/api/events/" + event + "/suspend", admin, Map.of("reason", "Checking the venue"))
                .assertStatus(200);

        JsonNode seenByOrganizer = history(organizer, event, "?action=EVENT_SUSPENDED").get(0);
        assertThat(seenByOrganizer.get("actorName").asString()).isEqualTo("BiletFlow Support");
        assertThat(seenByOrganizer.get("actorEmail").isNull()).isTrue();
        assertThat(seenByOrganizer.get("actorId").isNull()).isTrue();
        assertThat(seenByOrganizer.get("actorRole").asString()).isEqualTo("PLATFORM_ADMIN");

        JsonNode seenByAdmin = history(admin, event, "?action=EVENT_SUSPENDED").get(0);
        assertThat(seenByAdmin.get("actorEmail").asString()).isEqualTo(emailOf(admin));

        // A suspended event's history stays readable for its organizer.
        assertThat(history(organizer, event, "")).hasSize(3);
    }

    @Test
    void organizerListsOwnEventsByPhase() {
        String organizer = organizer();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime earlierToday = now.minusMinutes(1).isBefore(todayStart) ? todayStart : now.minusMinutes(1);

        long upcoming = publishedEvent(organizer);
        long draft = draftEvent(organizer, "Draft", "Hall");
        long active = timed(organizer, now.minusHours(1), now.plusHours(1));
        long activeNoEnd = timed(organizer, earlierToday, null);
        long completed = timed(organizer, now.minusDays(3), now.minusDays(2));
        long completedNoEnd = timed(organizer, todayStart.minusDays(1).plusHours(12), null);
        long cancelled = publishedEvent(organizer);
        call(HttpMethod.POST, "/api/events/" + cancelled + "/cancel", organizer, null).assertStatus(200);
        publishedEvent(organizer()); // someone else's

        assertThat(myEvents(organizer, "?phase=UPCOMING")).containsExactlyInAnyOrder(upcoming, draft);
        assertThat(myEvents(organizer, "?phase=ACTIVE")).containsExactlyInAnyOrder(active, activeNoEnd);
        assertThat(myEvents(organizer, "?phase=COMPLETED")).containsExactlyInAnyOrder(completed, completedNoEnd);
        assertThat(myEvents(organizer, "?phase=CANCELLED")).containsExactly(cancelled);
        assertThat(myEvents(organizer, "")).hasSize(7);
        call(HttpMethod.GET, "/api/organizer/events?phase=SOON", organizer, null).assertStatus(400);
        call(HttpMethod.GET, "/api/organizer/events", register(), null).assertStatus(403);

        // Every event response carries its phase.
        assertThat(call(HttpMethod.GET, "/api/events/" + completed, organizer, null).assertStatus(200)
                .json().get("phase").asString()).isEqualTo("COMPLETED");
        assertThat(call(HttpMethod.GET, "/api/events/" + upcoming, null, null).assertStatus(200)
                .json().get("phase").asString()).isEqualTo("UPCOMING");
    }

    // A published event moved in time with SQL, since the API only accepts future start times.
    private long timed(String organizer, LocalDateTime start, LocalDateTime end) {
        long event = publishedEvent(organizer);
        jdbcTemplate.update("UPDATE events SET start_time = ?, end_time = ? WHERE id = ?", start, end, event);
        return event;
    }

    private List<Long> myEvents(String organizer, String query) {
        return list(call(HttpMethod.GET, "/api/organizer/events" + query, organizer, null).assertStatus(200)
                .json().get("content")).stream().map(e -> e.get("id").asLong()).toList();
    }

    private List<JsonNode> history(String token, long event, String query) {
        return list(call(HttpMethod.GET, "/api/organizer/events/" + event + "/history" + query, token, null)
                .assertStatus(200).json().get("content"));
    }
}
