package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.OutgoingMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Refunds with simulated payments (SRS 4.9). Bank refunds are covered in HalykPaymentIntegrationTest.
class RefundIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private CapturingMailGateway mailbox;

    private String organizer;
    private long event;
    private long type;
    private String buyer;
    private long orderId;

    @BeforeEach
    void paidOrder() {
        organizer = organizer();
        event = publishedEvent(organizer);
        type = ticketType(organizer, event, 2500, 10);
        buyer = register();
        orderId = createOrder(buyer, event, type, 2).get("id").asLong();
        pay(buyer, orderId).assertStatus(200);
    }

    @Test
    void organizerRefundsAPaidOrderAndItsTicketsStopAdmitting() {
        List<String> codes = tickets(buyer).stream().map(t -> t.get("code").asString()).toList();

        JsonNode order = refund(organizer, Map.of("reason", "Sold by mistake")).assertStatus(200).json();

        assertThat(order.get("status").asString()).isEqualTo("REFUNDED");
        assertThat(tickets(buyer)).extracting(t -> t.get("status").asString()).containsOnly("REFUNDED");
        JsonNode scan = scan(organizer, event, codes.get(0));
        assertThat(scan.get("result").asString()).isEqualTo("CANCELLED");
        assertThat(scan.get("message").asString()).isEqualTo("This ticket was refunded");
        long ticketId = tickets(buyer).get(0).get("id").asLong();
        call(HttpMethod.GET, "/api/tickets/" + ticketId + "/pdf", buyer, null).assertStatus(400);

        List<JsonNode> refunds = refunds(buyer);
        assertThat(refunds).hasSize(1);
        JsonNode refund = refunds.get(0);
        assertThat(refund.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(refund.get("amount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(refund.get("provider").asString()).isEqualTo("SIMULATED");
        assertThat(refund.get("simulated").asBoolean()).isTrue();
        assertThat(refund.get("reference").asString()).startsWith("SIM-REFUND-");
        assertThat(refund.get("reason").asString()).isEqualTo("Sold by mistake");
        assertThat(refund.get("completedAt").isNull()).isFalse();

        assertThat(notificationTypes(buyer)).contains("REFUND_COMPLETED");
        assertThat(auditActions(event)).contains("ORDER_REFUNDED");
    }

    @Test
    void refundEmailTellsTheBuyerAndAttachesNoTickets() {
        deliverQueuedEmails(); // the order confirmation
        refund(organizer, Map.of("reason", "Sold by mistake")).assertStatus(200);
        deliverQueuedEmails();

        OutgoingMail mail = mailbox.sentTo(emailOf(buyer)).stream()
                .filter(m -> m.subject().startsWith("Refund for"))
                .findFirst().orElseThrow();
        assertThat(mail.text()).contains("5000.00 KZT", "Sold by mistake", "demonstration refund");
        assertThat(mail.attachment()).isNull();
    }

    @Test
    void refundedTicketsGoBackOnSale() {
        long smallType = ticketType(organizer, event, 1000, 2);
        long smallOrder = createOrder(buyer, event, smallType, 2).get("id").asLong();
        pay(buyer, smallOrder).assertStatus(200);
        String other = register();
        call(HttpMethod.POST, "/api/orders", other, orderBody(event, smallType, 1)).assertStatus(400);

        call(HttpMethod.POST, "/api/orders/" + smallOrder + "/refund", organizer, null).assertStatus(200);

        createOrder(other, event, smallType, 2);
    }

    @Test
    void onlyTheEventsOrganizerOrAPlatformAdminCanRefund() {
        refund(buyer, null).assertStatus(403);
        refund(organizer(), null).assertStatus(404); // another organizer must not learn the order exists
        call(HttpMethod.GET, "/api/orders/" + orderId + "/refunds", register(), null).assertStatus(404);

        refund(admin(), null).assertStatus(200);
        assertThat(list(call(HttpMethod.GET, "/api/orders/" + orderId + "/refunds", organizer, null)
                .assertStatus(200).json())).hasSize(1);
    }

    @Test
    void anOrderIsRefundedOnlyOnceEvenWhenAskedConcurrently() throws Exception {
        List<Integer> statuses = concurrently(5, () -> refund(organizer, null).status());

        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses).filteredOn(s -> s != 200).containsOnly(400);
        assertThat(refunds(buyer)).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'ORDER_REFUNDED' AND entity_id = ?", Long.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void checkedInOrderIsRefundedOnlyAfterTheCheckInIsUndone() {
        JsonNode ticket = tickets(buyer).get(0);
        scan(organizer, event, ticket.get("code").asString());

        assertThat(refund(organizer, null).assertStatus(400).text()).contains("already checked in");

        call(HttpMethod.POST, "/api/check-in/events/" + event + "/tickets/" + ticket.get("id").asLong() + "/undo",
                organizer, null).assertStatus(200);
        refund(organizer, null).assertStatus(200);
    }

    @Test
    void unpaidOrderCannotBeRefunded() {
        long pending = createOrder(buyer, event, type, 1).get("id").asLong();

        call(HttpMethod.POST, "/api/orders/" + pending + "/refund", organizer, null).assertStatus(400);
    }

    @Test
    void freeRegistrationIsCancelledWithoutARefundRecord() {
        long freeType = ticketType(organizer, event, 0, 10);
        String guest = register();
        long registration = createOrder(guest, event, freeType, 1).get("id").asLong();

        JsonNode order = call(HttpMethod.POST, "/api/orders/" + registration + "/refund", organizer,
                Map.of("reason", "Duplicate registration")).assertStatus(200).json();

        assertThat(order.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(tickets(guest)).extracting(t -> t.get("status").asString()).containsOnly("CANCELLED");
        assertThat(list(call(HttpMethod.GET, "/api/orders/" + registration + "/refunds", guest, null)
                .assertStatus(200).json())).isEmpty();
        assertThat(notificationTypes(guest)).contains("REGISTRATION_CANCELLED");
        assertThat(auditActions(event)).contains("REGISTRATION_CANCELLED");
    }

    @Test
    void ordersOfACancelledEventCanStillBeRefunded() {
        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);
        assertThat(tickets(buyer)).extracting(t -> t.get("status").asString()).containsOnly("CANCELLED");

        refund(organizer, null).assertStatus(200);

        assertThat(tickets(buyer)).extracting(t -> t.get("status").asString()).containsOnly("REFUNDED");
    }

    @Test
    void salesReportSubtractsRefunds() {
        String second = register();
        long secondOrder = createOrder(second, event, type, 1).get("id").asLong();
        pay(second, secondOrder).assertStatus(200);

        refund(organizer, null).assertStatus(200);

        JsonNode sales = call(HttpMethod.GET, "/api/organizer/events/" + event + "/sales", organizer, null)
                .assertStatus(200).json();
        assertThat(sales.get("paidOrders").asLong()).isEqualTo(1);
        assertThat(sales.get("refundedOrders").asLong()).isEqualTo(1);
        assertThat(sales.get("ticketsSold").asLong()).isEqualTo(1);
        assertThat(sales.get("ticketsRefunded").asLong()).isEqualTo(2);
        assertThat(sales.get("revenue").decimalValue()).isEqualByComparingTo("7500");
        assertThat(sales.get("refunds").decimalValue()).isEqualByComparingTo("5000");
        assertThat(sales.get("netRevenue").decimalValue()).isEqualByComparingTo("2500");
        assertThat(sales.get("processingFees").decimalValue()).isEqualByComparingTo("62.50"); // 2.5% of 2500 only
        assertThat(sales.get("estimatedPayout").decimalValue()).isEqualByComparingTo("2437.50");
        JsonNode typeSales = sales.get("ticketTypes").get(0);
        assertThat(typeSales.get("sold").asLong()).isEqualTo(1);
        assertThat(typeSales.get("refunded").asLong()).isEqualTo(2);
        assertThat(typeSales.get("available").asLong()).isEqualTo(9);

        JsonNode summary = call(HttpMethod.GET, "/api/organizer/sales/events", organizer, null).assertStatus(200).json();
        assertThat(summary.get("totals").get("refunds").decimalValue()).isEqualByComparingTo("5000");
        assertThat(summary.get("totals").get("netRevenue").decimalValue()).isEqualByComparingTo("2500");
    }

    @Test
    void refundPolicyIsShownOnTheEventAndCopiedByDuplicate() {
        String policy = "Full refund up to 7 days before the event.";
        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("refundPolicy", policy)).assertStatus(200);

        assertThat(call(HttpMethod.GET, "/api/events/" + event, null, null).assertStatus(200)
                .json().get("refundPolicy").asString()).isEqualTo(policy);
        Response copy = call(HttpMethod.POST, "/api/events/" + event + "/duplicate", organizer, null);
        assertThat(copy.status()).isIn(200, 201);
        assertThat(copy.json().get("refundPolicy").asString()).isEqualTo(policy);

        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("refundPolicy", "x".repeat(2001))).assertStatus(400);
    }

    private Response refund(String token, Object body) {
        return call(HttpMethod.POST, "/api/orders/" + orderId + "/refund", token, body);
    }

    private List<JsonNode> refunds(String token) {
        return list(call(HttpMethod.GET, "/api/orders/" + orderId + "/refunds", token, null).assertStatus(200).json());
    }
}
