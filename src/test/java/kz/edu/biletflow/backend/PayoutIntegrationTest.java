package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.PayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Simulated organizer payouts (SRS 3.2, 4.10). Events are moved into the past with SQL, since the API only
// accepts future start times; runPayouts() is what PayoutScheduler calls.
class PayoutIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private PayoutService payoutService;

    @Test
    void finishedEventIsScheduledThenPaidOutWithItsBreakdown() {
        String organizer = organizer();
        addPayoutAccount(organizer);
        String title = "Payout " + marker();
        long event = publishedEvent(organizer, title, "Hall");
        long type = ticketType(organizer, event, 5000, 10);
        long kept = paidOrder(register(), event, type, 2);
        long refunded = paidOrder(register(), event, type, 1);
        call(HttpMethod.POST, "/api/orders/" + refunded + "/refund", organizer, Map.of("reason", "Can't come"))
                .assertStatus(200);
        BigDecimal expected = money(call(HttpMethod.GET, "/api/organizer/events/" + event + "/sales", organizer, null)
                .assertStatus(200).json().get("estimatedPayout"));

        // Not before the event has ended.
        payoutService.runPayouts();
        call(HttpMethod.GET, "/api/organizer/events/" + event + "/payout", organizer, null).assertStatus(404);

        endedHoursAgo(event, 2); // still inside the 72-hour payout delay
        payoutService.runPayouts();
        JsonNode scheduled = eventPayout(organizer, event);
        assertThat(scheduled.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(money(scheduled.get("grossRevenue"))).isEqualByComparingTo("15000");
        assertThat(money(scheduled.get("refunds"))).isEqualByComparingTo("5000");
        assertThat(money(scheduled.get("amount"))).isEqualByComparingTo(expected);
        assertThat(scheduled.get("reference").isNull()).isTrue();
        assertThat(scheduled.get("simulated").asBoolean()).isTrue();
        assertThat(LocalDateTime.parse(scheduled.get("scheduledFor").asString())).isAfter(LocalDateTime.now());
        assertThat(notificationTitles(organizer)).contains("Payout scheduled for " + title);

        dueNow(event);
        payoutService.runPayouts();
        JsonNode paid = eventPayout(organizer, event);
        assertThat(paid.get("status").asString()).isEqualTo("PAID");
        assertThat(money(paid.get("amount"))).isEqualByComparingTo(expected);
        assertThat(money(paid.get("amount"))).isEqualByComparingTo(
                money(paid.get("grossRevenue")).subtract(money(paid.get("discounts"))).subtract(money(paid.get("refunds")))
                        .subtract(money(paid.get("processingFees"))));
        assertThat(paid.get("reference").asString()).startsWith("SIM-PAYOUT-");
        assertThat(paid.get("bankName").asString()).isEqualTo("Halyk Bank");
        assertThat(paid.get("ibanLast4").asString()).isEqualTo("4567");
        assertThat(paid.get("paidAt").isNull()).isFalse();
        assertThat(notificationTitles(organizer)).contains("Payout for " + title + " paid");
        assertThat(auditActions(event)).contains("PAYOUT_SCHEDULED", "PAYOUT_PAID");

        // Scheduler actions appear as "BiletFlow" in the organizer's event history.
        JsonNode history = call(HttpMethod.GET, "/api/organizer/events/" + event + "/history?entityType=PAYOUT",
                organizer, null).assertStatus(200).json();
        assertThat(list(history.get("content"))).extracting(e -> e.get("actorName").asString()).containsOnly("BiletFlow");

        // Paid-out money can't be refunded any more; running again changes nothing.
        Response refund = call(HttpMethod.POST, "/api/orders/" + kept + "/refund", organizer, null).assertStatus(400);
        assertThat(refund.json().get("message").asString()).contains("already paid out");
        payoutService.runPayouts();
        assertThat(eventPayout(organizer, event).get("reference").asString()).isEqualTo(paid.get("reference").asString());

        JsonNode mine = call(HttpMethod.GET, "/api/organizer/payouts?status=PAID", organizer, null).assertStatus(200).json();
        assertThat(list(mine.get("content"))).extracting(p -> p.get("eventId").asLong()).containsExactly(event);
    }

    @Test
    void automaticHoldsClearThemselves() {
        String organizer = organizer(); // no payout account yet
        String title = "Held " + marker();
        long event = publishedEvent(organizer, title, "Hall");
        long type = ticketType(organizer, event, 3000, 10);
        paidOrder(register(), event, type, 1);
        endedHoursAgo(event, 100); // past the payout delay: due at once

        payoutService.runPayouts();
        JsonNode held = eventPayout(organizer, event);
        assertThat(held.get("status").asString()).isEqualTo("ON_HOLD");
        assertThat(held.get("holdType").asString()).isEqualTo("NO_PAYOUT_ACCOUNT");
        assertThat(held.get("statusReason").asString()).contains("payout account");
        payoutService.runPayouts();
        assertThat(notificationTitles(organizer)).containsOnlyOnce("Payout for " + title + " is on hold");

        // An admin suspension takes over as the reason; lifting it (account now added) lets the payout through.
        String admin = admin();
        call(HttpMethod.POST, "/api/events/" + event + "/suspend", admin, Map.of("reason", "Checking")).assertStatus(200);
        addPayoutAccount(organizer);
        payoutService.runPayouts();
        assertThat(eventPayout(organizer, event).get("holdType").asString()).isEqualTo("EVENT_SUSPENDED");

        call(HttpMethod.POST, "/api/events/" + event + "/unsuspend", admin, null).assertStatus(200);
        payoutService.runPayouts();
        JsonNode paid = eventPayout(organizer, event);
        assertThat(paid.get("status").asString()).isEqualTo("PAID");
        assertThat(paid.get("holdType").isNull()).isTrue();
        assertThat(paid.get("statusReason").isNull()).isTrue();
    }

    @Test
    void adminHoldsReleasesAndPaysNow() {
        String organizer = organizer();
        addPayoutAccount(organizer);
        long event = publishedEvent(organizer, "Admin hold " + marker(), "Hall");
        long type = ticketType(organizer, event, 2000, 10);
        paidOrder(register(), event, type, 1);
        endedHoursAgo(event, 1);
        payoutService.runPayouts();
        long payout = eventPayout(organizer, event).get("id").asLong();
        String admin = admin();
        String path = "/api/admin/payouts/" + payout;

        call(HttpMethod.POST, path + "/hold", admin, Map.of("reason", " ")).assertStatus(400);
        call(HttpMethod.POST, path + "/hold", organizer, Map.of("reason", "x")).assertStatus(403);
        JsonNode held = call(HttpMethod.POST, path + "/hold", admin, Map.of("reason", "Chargeback review"))
                .assertStatus(200).json();
        assertThat(held.get("holdType").asString()).isEqualTo("ADMIN");
        assertThat(held.get("statusReason").asString()).isEqualTo("Chargeback review");
        call(HttpMethod.POST, path + "/hold", admin, Map.of("reason", "again")).assertStatus(400);

        // The scheduler leaves admin holds alone, even when due; pay-now needs a release first.
        dueNow(event);
        payoutService.runPayouts();
        assertThat(call(HttpMethod.GET, path, admin, null).assertStatus(200).json().get("status").asString())
                .isEqualTo("ON_HOLD");
        call(HttpMethod.POST, path + "/pay-now", admin, null).assertStatus(400);

        // Released after its date: paid at once.
        JsonNode released = call(HttpMethod.POST, path + "/release", admin, null).assertStatus(200).json();
        assertThat(released.get("status").asString()).isEqualTo("PAID");
        call(HttpMethod.POST, path + "/release", admin, null).assertStatus(400);
        call(HttpMethod.POST, path + "/hold", admin, Map.of("reason", "late")).assertStatus(400);
        call(HttpMethod.POST, path + "/pay-now", admin, null).assertStatus(400);
        assertThat(auditActions(event)).contains("PAYOUT_HELD", "PAYOUT_RELEASED", "PAYOUT_PAID");
    }

    @Test
    void payNowSkipsTheWaitButNotTheChecks() {
        String organizer = organizer();
        long event = publishedEvent(organizer, "Pay now " + marker(), "Hall");
        long type = ticketType(organizer, event, 1000, 10);
        paidOrder(register(), event, type, 3);
        endedHoursAgo(event, 1);
        payoutService.runPayouts();
        String admin = admin();
        long payout = eventPayout(organizer, event).get("id").asLong();

        // No payout account: pay-now puts it on hold with that reason instead of paying.
        JsonNode blocked = call(HttpMethod.POST, "/api/admin/payouts/" + payout + "/pay-now", admin, null)
                .assertStatus(200).json();
        assertThat(blocked.get("holdType").asString()).isEqualTo("NO_PAYOUT_ACCOUNT");

        addPayoutAccount(organizer);
        JsonNode paid = call(HttpMethod.POST, "/api/admin/payouts/" + payout + "/pay-now", admin, null)
                .assertStatus(200).json();
        assertThat(paid.get("status").asString()).isEqualTo("PAID");
        assertThat(LocalDateTime.parse(paid.get("scheduledFor").asString())).isAfter(LocalDateTime.now());
    }

    @Test
    void cancelledOrFullyRefundedEventsAreNotPaidOut() {
        String organizer = organizer();
        addPayoutAccount(organizer);

        // Refunded before it ended, like a free event: no paid sales, so no payout at all.
        long refundedEarly = publishedEvent(organizer, "Refunded early", "Hall");
        long type = ticketType(organizer, refundedEarly, 4000, 10);
        long order = paidOrder(register(), refundedEarly, type, 1);
        call(HttpMethod.POST, "/api/orders/" + order + "/refund", organizer, null).assertStatus(200);
        endedHoursAgo(refundedEarly, 100);

        long freeEvent = publishedEvent(organizer, "Free", "Hall");
        long freeType = ticketType(organizer, freeEvent, 0, 10);
        createOrder(register(), freeEvent, freeType, 1);
        endedHoursAgo(freeEvent, 100);

        // Refunded while the payout waited: refunds are still allowed then, and nothing is left to pay.
        long refundedLate = publishedEvent(organizer, "Refunded late", "Hall");
        long type3 = ticketType(organizer, refundedLate, 4000, 10);
        long order3 = paidOrder(register(), refundedLate, type3, 1);
        endedHoursAgo(refundedLate, 1);

        // Cancelled after its payout was scheduled: the payout is cancelled, and its orders can still be refunded.
        long cancelledEvent = publishedEvent(organizer, "Cancelled later", "Hall");
        long type2 = ticketType(organizer, cancelledEvent, 4000, 10);
        long order2 = paidOrder(register(), cancelledEvent, type2, 1);
        endedHoursAgo(cancelledEvent, 1);

        payoutService.runPayouts();
        call(HttpMethod.GET, "/api/organizer/events/" + refundedEarly + "/payout", organizer, null).assertStatus(404);
        call(HttpMethod.GET, "/api/organizer/events/" + freeEvent + "/payout", organizer, null).assertStatus(404);
        assertThat(eventPayout(organizer, refundedLate).get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(eventPayout(organizer, cancelledEvent).get("status").asString()).isEqualTo("SCHEDULED");

        call(HttpMethod.POST, "/api/orders/" + order3 + "/refund", organizer, null).assertStatus(200);
        dueNow(refundedLate);
        payoutService.runPayouts();
        JsonNode nothing = eventPayout(organizer, refundedLate);
        assertThat(nothing.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(nothing.get("statusReason").asString()).contains("refunded");
        assertThat(money(nothing.get("amount"))).isEqualByComparingTo("0");

        call(HttpMethod.POST, "/api/events/" + cancelledEvent + "/cancel", organizer, null).assertStatus(200);
        dueNow(cancelledEvent);
        payoutService.runPayouts();
        JsonNode cancelled = eventPayout(organizer, cancelledEvent);
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("statusReason").asString()).contains("cancelled");
        call(HttpMethod.POST, "/api/orders/" + order2 + "/refund", organizer, null).assertStatus(200);
    }

    @Test
    void payoutsAreVisibleOnlyToTheirOrganizerAndAdmins() {
        String organizer = organizer();
        addPayoutAccount(organizer);
        String marker = marker();
        String title = "Visible " + marker;
        long event = publishedEvent(organizer, title, "Hall");
        long type = ticketType(organizer, event, 2500, 10);
        paidOrder(register(), event, type, 2);
        endedHoursAgo(event, 100);
        payoutService.runPayouts();
        String admin = admin();

        call(HttpMethod.GET, "/api/organizer/events/" + event + "/payout", organizer(), null).assertStatus(404);
        call(HttpMethod.GET, "/api/organizer/events/" + event + "/payout", register(), null).assertStatus(403);
        call(HttpMethod.GET, "/api/organizer/events/" + event + "/payout", admin, null).assertStatus(200);
        call(HttpMethod.GET, "/api/organizer/payouts", register(), null).assertStatus(403);
        call(HttpMethod.GET, "/api/admin/payouts", organizer, null).assertStatus(403);
        assertThat(call(HttpMethod.GET, "/api/organizer/payouts", organizer(), null).assertStatus(200).json()
                .get("totalElements").asLong()).isZero();

        JsonNode found = call(HttpMethod.GET, "/api/admin/payouts?status=PAID&q=" + marker.toUpperCase(), admin, null)
                .assertStatus(200).json();
        assertThat(list(found.get("content"))).extracting(p -> p.get("eventId").asLong()).containsExactly(event);
        assertThat(call(HttpMethod.GET, "/api/admin/payouts?q=" + event, admin, null).assertStatus(200).json()
                .get("totalElements").asLong()).isPositive();
        call(HttpMethod.GET, "/api/admin/payouts/999999999", admin, null).assertStatus(404);

        JsonNode overview = call(HttpMethod.GET, "/api/admin/overview", admin, null).assertStatus(200).json();
        assertThat(overview.has("scheduledPayouts")).isTrue();
        assertThat(overview.has("payoutsOnHold")).isTrue();

        LocalDate today = LocalDate.now();
        Response csv = call(HttpMethod.GET, "/api/admin/reports/payouts.csv?from=" + today.minusDays(10) + "&to=" + today,
                admin, null).assertStatus(200);
        assertThat(csv.contentType().toString()).startsWith("text/csv");
        String text = csv.text();
        assertThat(text).contains("payout_id,event_id,event_title", title, "SIM-PAYOUT-");
        assertThat(text.lines().filter(line -> line.contains(title)).findFirst().orElseThrow()).contains(",PAID,", ",true,");
    }

    // helpers //

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void addPayoutAccount(String organizer) {
        call(HttpMethod.PUT, "/api/organizers/profile/payout-account", organizer, Map.of(
                "bankName", "Halyk Bank", "accountHolder", "Test Organizer", "iban", "KZ123456789012344567"))
                .assertStatus(200);
    }

    private long paidOrder(String buyer, long event, long type, int quantity) {
        long order = createOrder(buyer, event, type, quantity).get("id").asLong();
        pay(buyer, order).assertStatus(200);
        return order;
    }

    // Ended this many hours ago (it started 3 hours before that).
    private void endedHoursAgo(long event, int hours) {
        LocalDateTime end = LocalDateTime.now().minusHours(hours);
        jdbcTemplate.update("UPDATE events SET start_time = ?, end_time = ?, registration_opening_time = NULL,"
                + " registration_closing_time = NULL WHERE id = ?", end.minusHours(3), end, event);
    }

    private void dueNow(long event) {
        jdbcTemplate.update("UPDATE payouts SET scheduled_for = ? WHERE event_id = ?",
                LocalDateTime.now().minusMinutes(1), event);
    }

    private JsonNode eventPayout(String organizer, long event) {
        return call(HttpMethod.GET, "/api/organizer/events/" + event + "/payout", organizer, null).assertStatus(200).json();
    }

    private List<String> notificationTitles(String user) {
        return notifications(user).stream().map(n -> n.get("title").asString()).toList();
    }

    private static BigDecimal money(JsonNode value) {
        return new BigDecimal(value.toString().replace("\"", ""));
    }
}
