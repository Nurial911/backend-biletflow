package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.PaymentSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Paying through Halyk's page, against a fake bank that answers like the real test environment.
@TestPropertySource(properties = {
        "app.payments.provider=halyk",
        "app.payments.halyk.client-id=test-client",
        "app.payments.halyk.client-secret=test-client-secret",
        "app.payments.halyk.terminal-id=" + FakeHalykServer.TERMINAL,
        "app.payments.halyk.callback-base-url=https://biletflow.example.test",
        "app.payments.halyk.settle-delay-ms=3600000"
})
class HalykPaymentIntegrationTest extends ApiIntegrationTest {
    private static final FakeHalykServer BANK = FakeHalykServer.start();

    @DynamicPropertySource
    static void bank(DynamicPropertyRegistry registry) {
        registry.add("app.payments.halyk.oauth-url", () -> BANK.url("/oauth2/token"));
        registry.add("app.payments.halyk.api-url", () -> BANK.url(""));
    }

    @Autowired
    private PaymentSessionService paymentSessionService;

    private String buyer;
    private long orderId;

    @BeforeEach
    void orderWaitingForPayment() {
        BANK.down = false;
        BANK.refusesCharges = false;
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 2500, 10);
        buyer = register();
        orderId = createOrder(buyer, event, type, 2).get("id").asLong();
    }

    @Test
    void sessionGivesTheBrowserWhatItNeedsAndKeepsSecretsOnTheServer() {
        Response response = call(HttpMethod.POST, sessionPath(), buyer, null).assertStatus(200);

        JsonNode session = response.json();
        JsonNode payment = session.get("payment");
        String invoice = payment.get("invoiceId").asString();
        assertThat(session.get("provider").asString()).isEqualTo("HALYK");
        assertThat(session.get("simulated").asBoolean()).isTrue();
        assertThat(session.get("scriptUrl").asString()).isNotBlank();
        assertThat(invoice).matches("\\d{15}");
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(payment.get("currency").asString()).isEqualTo("KZT");
        assertThat(payment.get("terminal").asString()).isEqualTo(FakeHalykServer.TERMINAL);
        assertThat(payment.get("email").asString()).isEqualTo(emailOf(buyer));
        assertThat(payment.get("postLink").asString()).isEqualTo("https://biletflow.example.test/api/payments/halyk/callback");
        assertThat(payment.get("backLink").asString()).contains("/orders/" + orderId + "/payment-result");
        assertThat(payment.get("auth").get("access_token").asString()).isNotBlank();
        assertThat(BANK.tokenAmountOf(invoice)).isEqualTo("5000");
        assertThat(response.text()).doesNotContain("test-client-secret", BANK.secretOf(invoice));
        assertThat(orderStatus()).isEqualTo("PENDING");
    }

    @Test
    void buyersCannotMarkTheirOwnOrderPaid() {
        String invoice = openSession();

        pay(buyer, orderId).assertStatus(400);
        callback(invoice, "wrong-secret").assertStatus(403);
        callback("000000000000000", BANK.secretOf(invoice)).assertStatus(404);
        callback(invoice, BANK.secretOf(invoice)).assertStatus(200); // the real secret, but the bank has no payment

        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(tickets(buyer)).isEmpty();
    }

    @Test
    void forgedCallbackForAPaidLookingOrderStillAsksTheBank() {
        String invoice = openSession();
        BANK.authorize(invoice, 5000);

        callback(invoice, "wrong-secret").assertStatus(403);

        assertThat(BANK.statusOf(invoice)).isEqualTo("AUTH");
        assertThat(orderStatus()).isEqualTo("PENDING");
    }

    @Test
    void heldMoneyIsCapturedAndTicketsIssuedExactlyOnce() throws Exception {
        String invoice = openSession();
        BANK.authorize(invoice, 5000);
        String secret = BANK.secretOf(invoice);

        List<Integer> statuses = concurrently(6, () -> callback(invoice, secret).status());

        assertThat(statuses).containsOnly(200);
        assertThat(BANK.statusOf(invoice)).isEqualTo("CHARGE");
        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(tickets(buyer)).hasSize(2);
        List<JsonNode> payments = payments(buyer, orderId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(payments.get(0).get("provider").asString()).isEqualTo("HALYK");
        assertThat(payments.get(0).get("simulated").asBoolean()).isTrue();
        assertThat(payments.get(0).get("reference").asString()).startsWith("HALYK-");
        assertThat(notificationTypes(buyer)).containsExactly("ORDER_CONFIRMED");
    }

    @Test
    void alreadyChargedPaymentIsFulfilledWithoutCapturingAgain() {
        String invoice = openSession();
        BANK.charged(invoice, 5000);

        callback(invoice, BANK.secretOf(invoice)).assertStatus(200);

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(BANK.operationCount("charge", invoice)).isZero();
    }

    @Test
    void lostCallbackIsCoveredByTheBackgroundCheck() {
        String invoice = openSession();
        BANK.authorize(invoice, 5000);

        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(tickets(buyer)).hasSize(2);
    }

    @Test
    void returnPageCanAskTheBankStraightAway() {
        String invoice = openSession();
        BANK.authorize(invoice, 5000);

        JsonNode order = call(HttpMethod.POST, checkPath(), buyer, null).assertStatus(200).json();

        assertThat(order.get("status").asString()).isEqualTo("PAID");
        call(HttpMethod.POST, checkPath(), register(), null).assertStatus(404);
    }

    @Test
    void eachDeclineIsRecordedOnceAndARetryOnTheSamePageCanSucceed() {
        String invoice = openSession();
        BANK.decline(invoice, 5000);

        paymentSessionService.settleOpenSessions();
        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(payments(buyer, orderId)).extracting(p -> p.get("status").asString()).containsExactly("FAILED");
        assertThat(notificationTypes(buyer)).containsExactly("PAYMENT_FAILED");

        BANK.authorize(invoice, 5000);
        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(payments(buyer, orderId)).extracting(p -> p.get("status").asString()).containsExactly("FAILED", "SUCCEEDED");
    }

    @Test
    void paymentOfTheWrongAmountIsReleasedAndIssuesNothing() {
        String invoice = openSession();
        BANK.authorize(invoice, 1);

        callback(invoice, BANK.secretOf(invoice)).assertStatus(200);

        assertThat(BANK.statusOf(invoice)).isEqualTo("CANCEL");
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(tickets(buyer)).isEmpty();
        JsonNode payment = payments(buyer, orderId).get(0);
        assertThat(payment.get("status").asString()).isEqualTo("FAILED");
        assertThat(payment.get("failureReason").asString()).contains("does not match");
    }

    @Test
    void paymentForACancelledOrderIsReleasedAndTheBuyerIsTold() {
        String invoice = openSession();
        call(HttpMethod.POST, "/api/orders/" + orderId + "/cancel", buyer, null).assertStatus(200);
        BANK.authorize(invoice, 5000);

        paymentSessionService.settleOpenSessions();

        assertThat(BANK.statusOf(invoice)).isEqualTo("CANCEL");
        assertThat(tickets(buyer)).isEmpty();
        assertThat(notificationTypes(buyer)).containsExactly("PAYMENT_FAILED");
        assertThat(sessionStatus(invoice)).isEqualTo("RELEASED");
    }

    @Test
    void moneyAlreadyTakenForAnExpiredOrderIsRefunded() {
        String invoice = openSession();
        jdbcTemplate.update("UPDATE orders SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), orderId);
        BANK.charged(invoice, 5000);

        paymentSessionService.settleOpenSessions();

        assertThat(BANK.statusOf(invoice)).isEqualTo("REFUND");
        assertThat(tickets(buyer)).isEmpty();
    }

    @Test
    void payingTwiceFromTwoTabsReleasesTheSecondPayment() {
        String first = openSession();
        String second = openSession();
        BANK.authorize(first, 5000);
        BANK.authorize(second, 5000);

        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(tickets(buyer)).hasSize(2);
        assertThat(List.of(BANK.statusOf(first), BANK.statusOf(second))).containsExactlyInAnyOrder("CHARGE", "CANCEL");
    }

    @Test
    void bankOutageLeavesTheOrderPayableAndIsRetriedLater() {
        BANK.down = true;
        call(HttpMethod.POST, sessionPath(), buyer, null).assertStatus(502);
        assertThat(orderStatus()).isEqualTo("PENDING");

        BANK.down = false;
        String invoice = openSession();
        BANK.authorize(invoice, 5000);

        BANK.down = true;
        paymentSessionService.settleOpenSessions();
        assertThat(orderStatus()).isEqualTo("PENDING");

        BANK.down = false;
        paymentSessionService.settleOpenSessions();
        assertThat(orderStatus()).isEqualTo("PAID");
    }

    @Test
    void ticketsWaitUntilTheBankActuallyCapturesTheMoney() {
        String invoice = openSession();
        BANK.authorize(invoice, 5000);
        BANK.refusesCharges = true;

        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(tickets(buyer)).isEmpty();
        assertThat(sessionStatus(invoice)).isEqualTo("CAPTURING");

        BANK.refusesCharges = false;
        paymentSessionService.settleOpenSessions();

        assertThat(orderStatus()).isEqualTo("PAID");
        assertThat(tickets(buyer)).hasSize(2);
    }

    @Test
    void reservationOutlastsTheBankPageButRepeatVisitsCannotHoldTicketsForever() {
        jdbcTemplate.update("UPDATE orders SET created_at = ?, expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(30), LocalDateTime.now().plusMinutes(1), orderId);

        openSession();

        LocalDateTime expiresAt = LocalDateTime.parse(
                call(HttpMethod.GET, "/api/orders/" + orderId, buyer, null).json().get("expiresAt").asString());
        // Created 30 minutes ago with a 15-minute hold and a 20-minute page: 5 minutes from now, not 20.
        assertThat(expiresAt).isCloseTo(LocalDateTime.now().plusMinutes(5), within(30, ChronoUnit.SECONDS));
    }

    @Test
    void unusedSessionIsEventuallyClosed() {
        String invoice = openSession();
        jdbcTemplate.update("UPDATE payment_sessions SET expires_at = ? WHERE invoice_id = ?",
                LocalDateTime.now().minusMinutes(10), invoice);

        paymentSessionService.settleOpenSessions();

        assertThat(sessionStatus(invoice)).isEqualTo("ABANDONED");
    }

    @Test
    void onlyTheBuyerCanStartPaying() {
        call(HttpMethod.POST, sessionPath(), register(), null).assertStatus(404);
        call(HttpMethod.POST, sessionPath(), null, null).assertStatus(401);
    }

    // helpers //

    private String sessionPath() {
        return "/api/orders/" + orderId + "/payment-session";
    }

    private String checkPath() {
        return "/api/orders/" + orderId + "/payment-check";
    }

    private String openSession() {
        return call(HttpMethod.POST, sessionPath(), buyer, null).assertStatus(200).json().get("payment").get("invoiceId").asString();
    }

    private Response callback(String invoice, String secret) {
        return call(HttpMethod.POST, "/api/payments/halyk/callback", null,
                Map.of("invoiceId", invoice, "secret_hash", secret, "code", "ok"));
    }

    private String orderStatus() {
        return call(HttpMethod.GET, "/api/orders/" + orderId, buyer, null).assertStatus(200).json().get("status").asString();
    }

    private String sessionStatus(String invoice) {
        return jdbcTemplate.queryForObject("SELECT status FROM payment_sessions WHERE invoice_id = ?", String.class, invoice);
    }
}
