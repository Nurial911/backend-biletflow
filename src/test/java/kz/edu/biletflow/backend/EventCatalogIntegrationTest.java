package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// GET /api/events: server-side filters and minPrice, so the frontend doesn't load the whole catalog
// or ask for each card's ticket types. Every event here carries a unique token so other tests' data never matches.
class EventCatalogIntegrationTest extends ApiIntegrationTest {

    private final String tok = "cat" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final LocalDateTime jazzStart = LocalDateTime.now().plusDays(10).withHour(19).withMinute(0).withSecond(0).withNano(0);

    @Test
    void filtersCombineAndMinPriceIgnoresHiddenTypes() {
        String organizer = organizer();
        long hall = venue(organizer, "Hall " + tok, "Abai avenue 1, Almaty");
        long park = venue(organizer, "Park " + tok, "Mangilik El 5, Astana");

        long jazz = event(organizer, "Jazz night " + tok, "Music", hall, jazzStart);
        ticketType(organizer, jazz, 5000, 10);
        ticketType(organizer, jazz, 2000, 10);
        long hidden = ticketType(organizer, jazz, 1000, 10);
        jdbcTemplate.update("UPDATE ticket_types SET is_hidden = true WHERE id = ?", hidden);

        long lecture = event(organizer, "Open lecture " + tok, "Talks", park, jazzStart.plusDays(10));
        ticketType(organizer, lecture, 0, 50);

        long noTickets = event(organizer, "Announcement " + tok, "Talks", park, jazzStart.plusDays(20));

        // A draft is in its organizer's list only.
        draftOf(organizer, "Draft " + tok, hall, jazzStart);

        assertThat(titles(null, "q=" + tok)).containsExactlyInAnyOrder(
                "Jazz night " + tok, "Open lecture " + tok, "Announcement " + tok);
        assertThat(titles(organizer, "q=" + tok)).contains("Draft " + tok);
        assertThat(titles(admin(), "q=" + tok)).contains("Draft " + tok);
        assertThat(titles(register(), "q=" + tok)).doesNotContain("Draft " + tok);

        // q: case-insensitive, also the venue's name and address; category: exact, case-insensitive.
        assertThat(titles(null, "q=" + tok.toUpperCase())).hasSize(3);
        assertThat(titles(null, "q=" + tok + "&category=TALKS")).containsExactlyInAnyOrder(
                "Open lecture " + tok, "Announcement " + tok);
        assertThat(ids(null, "q=astana&venueId=" + park)).containsExactlyInAnyOrder(lecture, noTickets);
        assertThat(ids(null, "venueId=" + hall)).containsExactly(jazz);

        // Dates are inclusive days by start time.
        String day = jazzStart.toLocalDate().toString();
        assertThat(ids(null, "q=" + tok + "&from=" + day + "&to=" + day)).containsExactly(jazz);
        assertThat(ids(null, "q=" + tok + "&from=" + jazzStart.toLocalDate().plusDays(1))).containsExactlyInAnyOrder(lecture, noTickets);

        // Price: some visible ticket type in the range; a hidden type never matches.
        assertThat(ids(null, "q=" + tok + "&priceMax=0")).containsExactly(lecture);
        assertThat(ids(null, "q=" + tok + "&priceMin=1500&priceMax=2500")).containsExactly(jazz);
        assertThat(ids(null, "q=" + tok + "&priceMin=500&priceMax=1500")).isEmpty();
        assertThat(ids(null, "q=" + tok + "&priceMin=4000")).containsExactly(jazz);

        // minPrice: lowest visible price, 0 for free, null without ticket types; also on the event page.
        Map<Long, JsonNode> byId = new java.util.HashMap<>();
        page(null, "q=" + tok).forEach(e -> byId.put(e.get("id").asLong(), e));
        assertThat(byId.get(jazz).get("minPrice").asDouble()).isEqualTo(2000.0);
        assertThat(byId.get(lecture).get("minPrice").asDouble()).isEqualTo(0.0);
        assertThat(byId.get(noTickets).get("minPrice").isNull()).isTrue();
        assertThat(call(HttpMethod.GET, "/api/events/" + jazz, null, null).assertStatus(200).json()
                .get("minPrice").asDouble()).isEqualTo(2000.0);

        // Sorting by price and by date.
        assertThat(ids(null, "q=" + tok + "&priceMin=0&sort=minPrice,asc")).containsExactly(lecture, jazz);
        assertThat(ids(null, "q=" + tok + "&sort=startTime,desc")).containsExactly(noTickets, lecture, jazz);
    }

    @Test
    void badFiltersAreRefused() {
        call(HttpMethod.GET, "/api/events?from=2030-02-01&to=2030-01-01", null, null).assertStatus(400);
        call(HttpMethod.GET, "/api/events?priceMin=5000&priceMax=1000", null, null).assertStatus(400);
        call(HttpMethod.GET, "/api/events?priceMin=-1", null, null).assertStatus(400);
        call(HttpMethod.GET, "/api/events?from=tomorrow", null, null).assertStatus(400);
        // % and _ are literal, not wildcards.
        assertThat(call(HttpMethod.GET, "/api/events?q=%25%25" + tok, null, null).assertStatus(200).json()
                .get("content").size()).isZero();
    }

    // helpers //

    private long venue(String organizer, String name, String address) {
        return call(HttpMethod.POST, "/api/venues", organizer, Map.of("name", name, "address", address, "capacity", 500))
                .assertStatus(201).json().get("id").asLong();
    }

    private long draftOf(String organizer, String title, long venue, LocalDateTime start) {
        return call(HttpMethod.POST, "/api/events", organizer, Map.of(
                "title", title, "category", "Music", "startTime", start.toString(),
                "visibilityStatus", "PUBLIC", "venueId", venue))
                .assertStatus(201).json().get("id").asLong();
    }

    private long event(String organizer, String title, String category, long venue, LocalDateTime start) {
        long event = call(HttpMethod.POST, "/api/events", organizer, Map.of(
                "title", title, "category", category, "startTime", start.toString(),
                "visibilityStatus", "PUBLIC", "venueId", venue))
                .assertStatus(201).json().get("id").asLong();
        call(HttpMethod.POST, "/api/events/" + event + "/publish", organizer, null).assertStatus(200);
        return event;
    }

    private List<JsonNode> page(String token, String query) {
        return list(call(HttpMethod.GET, "/api/events?size=100&" + query, token, null).assertStatus(200).json().get("content"));
    }

    private List<String> titles(String token, String query) {
        return page(token, query).stream().map(e -> e.get("title").asString()).toList();
    }

    private List<Long> ids(String token, String query) {
        return page(token, query).stream().map(e -> e.get("id").asLong()).toList();
    }
}
