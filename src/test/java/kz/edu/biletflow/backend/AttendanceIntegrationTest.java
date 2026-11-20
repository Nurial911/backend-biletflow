package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Attendance analytics and sales-over-time filters for the organizer dashboard (SRS 4.15).
class AttendanceIntegrationTest extends ApiIntegrationTest {

    @Test
    void attendanceCountsHoldersCheckInsAndAbsentees() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long free = ticketType(organizer, event, 0, 10);
        long paid = ticketType(organizer, event, 1000, 10);
        String guest = register();
        createOrder(guest, event, free, 3);
        String buyer = register();
        pay(buyer, createOrder(buyer, event, paid, 2).get("id").asLong()).assertStatus(200);
        createOrder(register(), event, paid, 1); // unpaid: no ticket yet
        String refunded = register();
        long refundedOrder = createOrder(refunded, event, paid, 1).get("id").asLong();
        pay(refunded, refundedOrder).assertStatus(200);
        call(HttpMethod.POST, "/api/orders/" + refundedOrder + "/refund", organizer, null).assertStatus(200);

        List<JsonNode> guestTickets = tickets(guest);
        scan(organizer, event, guestTickets.get(0).get("code").asString());
        scan(organizer, event, guestTickets.get(1).get("code").asString());
        scan(organizer, event, tickets(buyer).get(0).get("code").asString());
        call(HttpMethod.POST, "/api/check-in/events/" + event + "/tickets/" + guestTickets.get(1).get("id").asLong() + "/undo",
                organizer, null).assertStatus(200);

        JsonNode attendance = attendance(organizer, event, "");

        assertThat(attendance.get("ticketHolders").asLong()).isEqualTo(5);
        assertThat(attendance.get("checkedIn").asLong()).isEqualTo(2);
        assertThat(attendance.get("absent").asLong()).isEqualTo(3);
        assertThat(attendance.get("checkInRate").decimalValue()).isEqualByComparingTo("40.0");
        assertThat(attendance.get("undoneCheckIns").asLong()).isEqualTo(1);
        assertThat(attendance.get("phase").asString()).isEqualTo("UPCOMING");
        assertThat(attendance.get("firstArrival").isNull()).isFalse();
        assertThat(attendance.get("intervalMinutes").asInt()).isEqualTo(15);

        List<JsonNode> types = list(attendance.get("ticketTypes"));
        assertThat(types).extracting(t -> t.get("ticketTypeId").asLong()).containsExactly(free, paid);
        assertThat(types.get(0).get("ticketHolders").asLong()).isEqualTo(3);
        assertThat(types.get(0).get("checkedIn").asLong()).isEqualTo(1);
        assertThat(types.get(0).get("checkInRate").decimalValue()).isEqualByComparingTo("33.3");
        assertThat(types.get(1).get("ticketHolders").asLong()).isEqualTo(2);
        assertThat(types.get(1).get("absent").asLong()).isEqualTo(1);

        List<JsonNode> arrivals = list(attendance.get("arrivals"));
        assertThat(arrivals.stream().mapToLong(a -> a.get("checkedIn").asLong()).sum()).isEqualTo(2);
        assertThat(arrivals.getLast().get("cumulative").asLong()).isEqualTo(2);

        JsonNode onlyPaid = attendance(organizer, event, "?ticketTypeId=" + paid);
        assertThat(onlyPaid.get("ticketHolders").asLong()).isEqualTo(2);
        assertThat(onlyPaid.get("checkedIn").asLong()).isEqualTo(1);
        assertThat(onlyPaid.get("undoneCheckIns").asLong()).isZero();
        assertThat(list(onlyPaid.get("ticketTypes"))).hasSize(1);
        assertThat(list(onlyPaid.get("arrivals")).getLast().get("cumulative").asLong()).isEqualTo(1);
    }

    @Test
    void arrivalsAreGroupedIntoAlignedIntervals() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String guest = register();
        createOrder(guest, event, type, 4);
        List<JsonNode> guestTickets = tickets(guest);
        LocalDateTime evening = LocalDateTime.of(2026, 3, 14, 18, 0);
        int[] minutes = {2, 7, 31};
        for (int i = 0; i < minutes.length; i++) {
            JsonNode ticket = guestTickets.get(i);
            scan(organizer, event, ticket.get("code").asString());
            jdbcTemplate.update("UPDATE tickets SET used_at = ? WHERE id = ?",
                    evening.plusMinutes(minutes[i]), ticket.get("id").asLong());
        }

        List<JsonNode> quarter = list(attendance(organizer, event, "").get("arrivals"));
        assertThat(quarter).extracting(a -> a.get("from").asString())
                .containsExactly("2026-03-14T18:00:00", "2026-03-14T18:30:00");
        assertThat(quarter).extracting(a -> a.get("to").asString())
                .containsExactly("2026-03-14T18:15:00", "2026-03-14T18:45:00");
        assertThat(quarter).extracting(a -> a.get("checkedIn").asLong()).containsExactly(2L, 1L);
        assertThat(quarter).extracting(a -> a.get("cumulative").asLong()).containsExactly(2L, 3L);

        List<JsonNode> hourly = list(attendance(organizer, event, "?interval=60").get("arrivals"));
        assertThat(hourly).extracting(a -> a.get("checkedIn").asLong()).containsExactly(3L);

        JsonNode totals = attendance(organizer, event, "");
        assertThat(totals.get("firstArrival").asString()).isEqualTo("2026-03-14T18:02:00");
        assertThat(totals.get("lastArrival").asString()).isEqualTo("2026-03-14T18:31:00");
        assertThat(totals.get("checkInRate").decimalValue()).isEqualByComparingTo("75.0");
    }

    @Test
    void attendanceIsOnlyForTheEventsOrganizerOrAPlatformAdmin() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String staff = eventAdmin();
        call(HttpMethod.POST, "/api/events/" + event + "/staff", organizer, Map.of("email", emailOf(staff)))
                .assertStatus(201);
        String path = "/api/organizer/events/" + event + "/attendance";

        call(HttpMethod.GET, path, organizer(), null).assertStatus(404);
        call(HttpMethod.GET, path, staff, null).assertStatus(403); // staff have /api/check-in/.../stats
        call(HttpMethod.GET, path, register(), null).assertStatus(403);
        call(HttpMethod.GET, path, null, null).assertStatus(401);
        call(HttpMethod.GET, path + "?interval=7", organizer, null).assertStatus(400);
        long foreignType = ticketType(organizer, publishedEvent(organizer), 0, 5);
        call(HttpMethod.GET, path + "?ticketTypeId=" + foreignType, organizer, null).assertStatus(404);

        JsonNode empty = attendance(admin(), event, "");
        assertThat(empty.get("ticketHolders").asLong()).isZero();
        assertThat(empty.get("checkInRate").decimalValue()).isEqualByComparingTo("0");
        assertThat(empty.get("firstArrival").isNull()).isTrue();
        assertThat(list(empty.get("arrivals"))).isEmpty();
    }

    @Test
    void salesOverTimeCanBeFilteredByDateRangeAndTicketType() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long standard = ticketType(organizer, event, 1000, 10);
        long vip = ticketType(organizer, event, 2000, 10);
        long first = paidOrder(event, List.of(Map.of("ticketTypeId", standard, "quantity", 2)));
        long second = paidOrder(event, List.of(Map.of("ticketTypeId", vip, "quantity", 1)));
        long mixed = paidOrder(event, List.of(Map.of("ticketTypeId", standard, "quantity", 1),
                Map.of("ticketTypeId", vip, "quantity", 1)));
        paidAt(first, LocalDateTime.of(2026, 1, 10, 12, 0));
        paidAt(second, LocalDateTime.of(2026, 1, 12, 12, 0));
        paidAt(mixed, LocalDateTime.of(2026, 1, 12, 15, 0));
        String base = "/api/organizer/events/" + event + "/sales";

        JsonNode all = sales(organizer, base);
        List<JsonNode> days = list(all.get("salesByDay"));
        assertThat(days).extracting(d -> d.get("date").asString()).containsExactly("2026-01-10", "2026-01-12");
        assertThat(days).extracting(d -> d.get("paidOrders").asLong()).containsExactly(1L, 2L);
        assertThat(days).extracting(d -> d.get("ticketsSold").asLong()).containsExactly(2L, 3L);
        assertThat(days.get(1).get("revenue").decimalValue()).isEqualByComparingTo("5000");

        assertThat(list(sales(organizer, base + "?from=2026-01-11").get("salesByDay")))
                .extracting(d -> d.get("date").asString()).containsExactly("2026-01-12");
        assertThat(list(sales(organizer, base + "?to=2026-01-10").get("salesByDay")))
                .extracting(d -> d.get("date").asString()).containsExactly("2026-01-10");

        JsonNode vipOnly = sales(organizer, base + "?ticketTypeId=" + vip);
        List<JsonNode> vipDays = list(vipOnly.get("salesByDay"));
        assertThat(vipDays).extracting(d -> d.get("date").asString()).containsExactly("2026-01-12");
        assertThat(vipDays.get(0).get("paidOrders").asLong()).isEqualTo(2);
        assertThat(vipDays.get(0).get("ticketsSold").asLong()).isEqualTo(2);
        assertThat(vipDays.get(0).get("revenue").decimalValue()).isEqualByComparingTo("4000");
        // The filters narrow the series only, never the totals.
        assertThat(vipOnly.get("revenue").decimalValue()).isEqualByComparingTo("7000");

        call(HttpMethod.GET, base + "?from=2026-01-12&to=2026-01-10", organizer, null).assertStatus(400);
        long foreignType = ticketType(organizer, publishedEvent(organizer), 500, 5);
        call(HttpMethod.GET, base + "?ticketTypeId=" + foreignType, organizer, null).assertStatus(404);
    }

    private long paidOrder(long event, List<Map<String, Object>> items) {
        String buyer = register();
        long order = call(HttpMethod.POST, "/api/orders", buyer, Map.of("eventId", event, "items", items))
                .assertStatus(201).json().get("id").asLong();
        pay(buyer, order).assertStatus(200);
        return order;
    }

    private void paidAt(long order, LocalDateTime when) {
        jdbcTemplate.update("UPDATE orders SET paid_at = ? WHERE id = ?", when, order);
    }

    private JsonNode sales(String organizer, String path) {
        return call(HttpMethod.GET, path, organizer, null).assertStatus(200).json();
    }

    private JsonNode attendance(String token, long event, String query) {
        return call(HttpMethod.GET, "/api/organizer/events/" + event + "/attendance" + query, token, null)
                .assertStatus(200).json();
    }
}
