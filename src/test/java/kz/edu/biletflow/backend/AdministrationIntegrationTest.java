package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Platform administration (SRS 4.12): search and monitoring, reported events, platform settings, CSV reports.
class AdministrationIntegrationTest extends ApiIntegrationTest {

    @Test
    void adminSearchesEventsOrdersPaymentsRefundsAndCampaigns() {
        String admin = admin();
        String organizer = organizer();
        String marker = "Adm" + UUID.randomUUID().toString().substring(0, 8);
        long event = publishedEvent(organizer, marker + " Night", "Hall");
        long type = ticketType(organizer, event, 1000, 20);
        String buyerEmail = "buyer-" + UUID.randomUUID() + "@test.kz";
        String buyer = register(buyerEmail);
        long declined = createOrder(buyer, event, type, 1).get("id").asLong();
        call(HttpMethod.POST, "/api/orders/" + declined + "/pay", buyer, Map.of("simulatedOutcome", "DECLINED"))
                .assertStatus(402);
        long paid = createOrder(buyer, event, type, 2).get("id").asLong();
        pay(buyer, paid).assertStatus(200);
        long refunded = createOrder(buyer, event, type, 1).get("id").asLong();
        pay(buyer, refunded).assertStatus(200);
        call(HttpMethod.POST, "/api/orders/" + refunded + "/refund", organizer, Map.of("reason", "Asked")).assertStatus(200);
        call(HttpMethod.POST, "/api/events/" + event + "/campaigns", organizer,
                Map.of("name", "Spring", "discountType", "PERCENT", "discountValue", 10)).assertStatus(201);

        // Events: by title, organizer email or id; drafts included.
        assertThat(ids(get(admin, "/api/admin/events?q=" + marker.toLowerCase()))).containsExactly(event);
        assertThat(ids(get(admin, "/api/admin/events?q=" + emailOf(organizer)))).contains(event);
        assertThat(ids(get(admin, "/api/admin/events?q=" + event))).contains(event);
        long draft = draftEvent(organizer, marker + " Draft", "Hall");
        assertThat(ids(get(admin, "/api/admin/events?q=" + marker + "&status=DRAFT"))).containsExactly(draft);

        // Orders: by buyer email, with status and event filters.
        assertThat(ids(get(admin, "/api/admin/orders?q=" + buyerEmail))).containsExactlyInAnyOrder(declined, paid, refunded);
        assertThat(ids(get(admin, "/api/admin/orders?status=PAID&eventId=" + event))).containsExactly(paid);
        JsonNode paidRow = list(get(admin, "/api/admin/orders?q=" + paid).get("content")).stream()
                .filter(o -> o.get("id").asLong() == paid).findFirst().orElseThrow();
        assertThat(paidRow.get("ticketCount").asInt()).isEqualTo(2);
        assertThat(paidRow.get("buyerEmail").asString()).isEqualTo(buyerEmail);
        assertThat(paidRow.get("eventTitle").asString()).isEqualTo(marker + " Night");

        // Payments: failures are found by status; by order id and provider.
        List<JsonNode> failed = list(get(admin, "/api/admin/payments?status=FAILED&q=" + buyerEmail).get("content"));
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).get("orderId").asLong()).isEqualTo(declined);
        assertThat(failed.get(0).get("failureReason").isNull()).isFalse();
        // A number also matches as text (references and emails contain digits), so look at this order's rows.
        assertThat(list(get(admin, "/api/admin/payments?q=" + paid + "&provider=simulated&size=100").get("content")))
                .filteredOn(p -> p.get("orderId").asLong() == paid)
                .extracting(p -> p.get("status").asString()).containsExactly("SUCCEEDED");
        assertThat(get(admin, "/api/admin/payments?q=" + buyerEmail + "&from=" + LocalDate.now().plusDays(1))
                .get("totalElements").asLong()).isZero();
        call(HttpMethod.GET, "/api/admin/payments?from=2026-02-02&to=2026-02-01", admin, null).assertStatus(400);

        // Refunds
        List<JsonNode> refunds = list(get(admin, "/api/admin/refunds?q=" + buyerEmail).get("content"));
        assertThat(refunds).extracting(r -> r.get("orderId").asLong()).containsExactly(refunded);
        assertThat(refunds.get(0).get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(refunds.get(0).get("reason").asString()).isEqualTo("Asked");

        // Promo-code activity
        assertThat(list(get(admin, "/api/admin/campaigns?eventId=" + event).get("content")))
                .extracting(c -> c.get("name").asString()).containsExactly("Spring");

        for (String path : List.of("/api/admin/events", "/api/admin/orders", "/api/admin/payments",
                "/api/admin/refunds", "/api/admin/campaigns", "/api/admin/overview", "/api/admin/settings",
                "/api/admin/event-reports", "/api/admin/reports/events.csv")) {
            call(HttpMethod.GET, path, organizer, null).assertStatus(403);
            call(HttpMethod.GET, path, null, null).assertStatus(401);
        }
    }

    @Test
    void overviewCountsWhatNeedsAttention() {
        String admin = admin();
        JsonNode before = get(admin, "/api/admin/overview");
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 5);
        String buyer = register();
        long order = createOrder(buyer, event, type, 1).get("id").asLong();
        call(HttpMethod.POST, "/api/orders/" + order + "/pay", buyer, Map.of("simulatedOutcome", "DECLINED"))
                .assertStatus(402);
        call(HttpMethod.POST, "/api/events/" + event + "/reports", buyer, Map.of("reason", "SPAM")).assertStatus(201);
        call(HttpMethod.POST, "/api/events/" + event + "/suspend", admin, null).assertStatus(200);

        JsonNode after = get(admin, "/api/admin/overview");
        assertThat(delta(before, after, "failedPaymentsLast24Hours")).isEqualTo(1);
        assertThat(delta(before, after, "failedPaymentsLast7Days")).isEqualTo(1);
        assertThat(delta(before, after, "openEventReports")).isEqualTo(1);
        assertThat(delta(before, after, "suspendedEvents")).isEqualTo(1);
        assertThat(delta(before, after, "users")).isEqualTo(2);
        assertThat(after.get("generatedAt").isNull()).isFalse();
    }

    @Test
    void usersReportEventsAndAdminsReviewThem() {
        String admin = admin();
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String reporter = register();

        JsonNode report = call(HttpMethod.POST, "/api/events/" + event + "/reports", reporter,
                Map.of("reason", "FRAUD", "message", "  Fake lineup  ")).assertStatus(201).json();
        long reportId = report.get("id").asLong();
        assertThat(report.get("status").asString()).isEqualTo("OPEN");
        assertThat(report.get("message").asString()).isEqualTo("Fake lineup");

        call(HttpMethod.POST, "/api/events/" + event + "/reports", reporter, Map.of("reason", "SPAM")).assertStatus(409);
        call(HttpMethod.POST, "/api/events/" + event + "/reports", organizer, Map.of("reason", "SPAM")).assertStatus(400);
        call(HttpMethod.POST, "/api/events/" + event + "/reports", reporter, Map.of("reason", "BORING")).assertStatus(400);
        call(HttpMethod.POST, "/api/events/" + event + "/reports", reporter, Map.of()).assertStatus(400);
        call(HttpMethod.POST, "/api/events/" + event + "/reports", null, Map.of("reason", "SPAM")).assertStatus(401);
        long draft = draftEvent(organizer, "Hidden draft", "Hall");
        call(HttpMethod.POST, "/api/events/" + draft + "/reports", reporter, Map.of("reason", "SPAM")).assertStatus(404);

        assertThat(ids(get(admin, "/api/admin/event-reports?status=OPEN&eventId=" + event))).containsExactly(reportId);
        assertThat(get(admin, "/api/admin/event-reports/" + reportId).get("reporterEmail").asString())
                .isEqualTo(emailOf(reporter));
        call(HttpMethod.GET, "/api/admin/event-reports/" + reportId, reporter, null).assertStatus(403);

        String path = "/api/admin/event-reports/" + reportId + "/review";
        call(HttpMethod.POST, path, admin, Map.of("outcome", "OPEN")).assertStatus(400);
        call(HttpMethod.POST, path, reporter, Map.of("outcome", "DISMISSED")).assertStatus(403);
        JsonNode reviewed = call(HttpMethod.POST, path, admin, Map.of("outcome", "ACTIONED", "note", "Suspending it"))
                .assertStatus(200).json();
        assertThat(reviewed.get("status").asString()).isEqualTo("ACTIONED");
        assertThat(reviewed.get("resolutionNote").asString()).isEqualTo("Suspending it");
        assertThat(reviewed.get("reviewedAt").isNull()).isFalse();
        call(HttpMethod.POST, path, admin, Map.of("outcome", "DISMISSED")).assertStatus(400); // already reviewed

        // The reporter is thanked, without the admin's note; the organizer's timeline doesn't show the report.
        JsonNode thanks = notifications(reporter).stream()
                .filter(n -> n.get("type").asString().equals("EVENT_REPORT_REVIEWED")).findFirst().orElseThrow();
        assertThat(thanks.get("body").asString()).contains("taken action").doesNotContain("Suspending it");
        assertThat(auditActions(event)).doesNotContain("EVENT_REPORT_REVIEWED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'EVENT_REPORT_REVIEWED' AND entity_id = ? AND event_id IS NULL",
                Long.class, reportId)).isEqualTo(1);

        // Once reviewed, the same user may report again.
        call(HttpMethod.POST, "/api/events/" + event + "/reports", reporter, Map.of("reason", "SPAM")).assertStatus(201);
        assertThat(ids(get(admin, "/api/admin/event-reports?eventId=" + event))).hasSize(2);
    }

    @Test
    void adminChangesFeesForFuturePaymentsOnly() {
        String admin = admin();
        JsonNode defaults = get(admin, "/api/admin/settings");
        assertThat(defaults.get("activationFeeKzt").decimalValue()).isEqualByComparingTo("5000");
        assertThat(defaults.get("processingFeePercent").decimalValue()).isEqualByComparingTo("2.5");

        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        String buyer = register();
        long before = createOrder(buyer, event, type, 2).get("id").asLong();
        pay(buyer, before).assertStatus(200);

        try {
            call(HttpMethod.PUT, "/api/admin/settings", admin, Map.of("processingFeePercent", 51)).assertStatus(400);
            call(HttpMethod.PUT, "/api/admin/settings", admin, Map.of("activationFeeKzt", -1)).assertStatus(400);
            call(HttpMethod.PUT, "/api/admin/settings", organizer, Map.of("activationFeeKzt", 1)).assertStatus(403);

            JsonNode changed = call(HttpMethod.PUT, "/api/admin/settings", admin,
                    Map.of("activationFeeKzt", 7000, "processingFeePercent", 4)).assertStatus(200).json();
            assertThat(changed.get("activationFeeKzt").decimalValue()).isEqualByComparingTo("7000");
            assertThat(changed.get("processingFeePercent").decimalValue()).isEqualByComparingTo("4");
            assertThat(changed.get("defaultActivationFeeKzt").decimalValue()).isEqualByComparingTo("5000");
            assertThat(changed.get("updatedAt").isNull()).isFalse();

            long after = createOrder(buyer, event, type, 1).get("id").asLong();
            pay(buyer, after).assertStatus(200);
            assertThat(payments(buyer, after).get(0).get("processingFee").decimalValue()).isEqualByComparingTo("40");
            assertThat(payments(buyer, before).get(0).get("processingFee").decimalValue()).isEqualByComparingTo("50");

            long other = publishedEvent(organizer);
            ticketTypeWithoutActivation(organizer, other, 500, 5);
            JsonNode activation = call(HttpMethod.POST, "/api/events/" + other + "/activation/pay-fee", organizer, null)
                    .assertStatus(200).json();
            assertThat(activation.get("feeAmount").decimalValue()).isEqualByComparingTo("7000");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT description FROM audit_log WHERE action = 'PLATFORM_SETTINGS_UPDATED' ORDER BY id DESC LIMIT 1",
                    String.class)).contains("activation fee 5000.00 → 7000.00 KZT").contains("processing fee 2.5% → 4%");
        } finally {
            // Settings are global and the test database is shared, so put the defaults back.
            call(HttpMethod.PUT, "/api/admin/settings", admin,
                    Map.of("activationFeeKzt", 5000, "processingFeePercent", 2.5)).assertStatus(200);
        }
    }

    @Test
    void adminDownloadsCsvReports() {
        String admin = admin();
        String organizer = organizer();
        String title = "=HYPERLINK(\"x\") Gala, \"VIP\"";
        long event = publishedEvent(organizer, title, "Hall");
        long type = ticketType(organizer, event, 1500, 10);
        String buyer = register();
        long order = createOrder(buyer, event, type, 2).get("id").asLong();
        pay(buyer, order).assertStatus(200);
        String reference = payments(buyer, order).get(0).get("reference").asString();
        LocalDate start = LocalDate.parse(get(organizer, "/api/events/" + event).get("startTime").asString().substring(0, 10));

        Response events = call(HttpMethod.GET, "/api/admin/reports/events.csv?from=" + start + "&to=" + start, admin, null)
                .assertStatus(200);
        assertThat(events.contentType().toString()).startsWith("text/csv");
        assertThat(events.body()[0] & 0xFF).isEqualTo(0xEF); // UTF-8 byte order mark, for Excel
        String[] lines = new String(events.body(), StandardCharsets.UTF_8).substring(1).split("\r\n");
        assertThat(lines[0]).startsWith("event_id,title,organizer_email,status");
        String row = List.of(lines).stream().filter(l -> l.startsWith(event + ",")).findFirst().orElseThrow();
        // Quoted because of the comma and quotes, and defused so a spreadsheet won't run it as a formula.
        assertThat(row).contains("\"'=HYPERLINK(\"\"x\"\") Gala, \"\"VIP\"\"\"");
        // capacity, paid/pending/refunded orders, sold/reserved/refunded tickets, revenue
        assertThat(row).contains(",500,1,0,0,2,0,0,3000.00,");

        Response payments = call(HttpMethod.GET, "/api/admin/reports/payments.csv", admin, null).assertStatus(200);
        String paymentRow = List.of(new String(payments.body(), StandardCharsets.UTF_8).split("\r\n")).stream()
                .filter(l -> l.contains(reference)).findFirst().orElseThrow();
        assertThat(paymentRow).contains(",SUCCEEDED,SIMULATED,true,3000.00,");

        call(HttpMethod.GET, "/api/admin/reports/events.csv?from=2025-01-01&to=2026-06-01", admin, null).assertStatus(400);
        call(HttpMethod.GET, "/api/admin/reports/payments.csv?from=2026-02-02&to=2026-02-01", admin, null).assertStatus(400);
    }

    private JsonNode get(String token, String path) {
        return call(HttpMethod.GET, path, token, null).assertStatus(200).json();
    }

    private static List<Long> ids(JsonNode page) {
        return list(page.get("content")).stream().map(n -> n.get("id").asLong()).toList();
    }

    private static long delta(JsonNode before, JsonNode after, String field) {
        return after.get(field).asLong() - before.get(field).asLong();
    }
}
