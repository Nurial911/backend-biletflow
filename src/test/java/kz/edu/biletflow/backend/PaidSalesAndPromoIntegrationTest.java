package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.impl.QrCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaidSalesAndPromoIntegrationTest extends ApiIntegrationTest {
    private static final String IBAN = "KZ123456789012345678";

    // paid-sales activation //

    @Test
    void paidTicketsCannotBeBoughtUntilTheWholeChecklistIsDone() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long paid = ticketTypeWithoutActivation(organizer, event, 5000, 10);
        long free = ticketTypeWithoutActivation(organizer, event, 0, 10);
        String attendee = register();
        String admin = admin();

        assertThat(call(HttpMethod.POST, "/api/orders", attendee, orderBody(event, paid, 1)).assertStatus(400).text())
                .contains("not on sale yet");
        createOrder(attendee, event, free, 1); // free tickets don't need activation

        JsonNode start = activation(organizer, event);
        assertThat(start.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(start.get("simulated").asBoolean()).isTrue();
        assertThat(start.get("readyToActivate").asBoolean()).isFalse();
        assertThat(doneItems(start)).containsExactly("PAID_TICKET_TYPE");
        assertThat(activationStep(organizer, event, "activate").assertStatus(400).text())
                .contains("identity verification", "payout account", "activation fee", "terms");

        verifyOrganizer(organizer, admin);
        registerPayoutAccount(organizer);
        JsonNode feePaid = activationStep(organizer, event, "pay-fee").assertStatus(200).json();
        assertThat(feePaid.get("feeReference").asString()).startsWith("SIM-");
        assertThat(feePaid.get("feeAmount").decimalValue()).isEqualByComparingTo("5000");
        activationStep(organizer, event, "pay-fee").assertStatus(400); // never charged twice
        activationStep(organizer, event, "accept-terms").assertStatus(200);

        assertThat(activation(organizer, event).get("readyToActivate").asBoolean()).isTrue();
        assertThat(activationStep(organizer, event, "activate").assertStatus(200).json().get("status").asString())
                .isEqualTo("ACTIVE");

        assertThat(call(HttpMethod.GET, "/api/events/" + event, null, null).json().get("paidSalesActive").asBoolean()).isTrue();
        long orderId = createOrder(attendee, event, paid, 1).get("id").asLong();
        pay(attendee, orderId).assertStatus(200);
        assertThat(auditActions(event)).contains("ACTIVATION_FEE_PAID", "ACTIVATION_TERMS_ACCEPTED", "PAID_SALES_ACTIVATED");
    }

    @Test
    void activationAppliesOnlyToTheSelectedEvent() {
        String organizer = organizer();
        long activated = publishedEvent(organizer);
        ticketTypeWithoutActivation(organizer, activated, 5000, 10);
        long other = publishedEvent(organizer);
        long otherType = ticketTypeWithoutActivation(organizer, other, 5000, 10);

        activateOverHttp(organizer, activated, admin());

        call(HttpMethod.POST, "/api/orders", register(), orderBody(other, otherType, 1)).assertStatus(400);
    }

    @Test
    void onlyTheOwnerCanSeeOrChangeAnActivation() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        ticketTypeWithoutActivation(organizer, event, 5000, 10);

        call(HttpMethod.GET, "/api/events/" + event + "/activation", null, null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + event + "/activation", organizer(), null).assertStatus(404);
        activationStep(organizer(), event, "pay-fee").assertStatus(403);
        activationStep(register(), event, "pay-fee").assertStatus(403);
    }

    @Test
    void organizerVerificationIsDecidedByAnAdmin() {
        String organizer = organizer();
        String admin = admin();
        long profile = call(HttpMethod.GET, "/api/organizers/profile", organizer, null).json().get("id").asLong();

        call(HttpMethod.POST, "/api/organizers/" + profile + "/verification", admin, Map.of("approved", true)).assertStatus(400); // nothing requested yet
        call(HttpMethod.POST, "/api/organizers/profile/verification", organizer, null).assertStatus(200);
        call(HttpMethod.POST, "/api/organizers/profile/verification", organizer, null).assertStatus(400);

        call(HttpMethod.POST, "/api/organizers/" + profile + "/verification", organizer, Map.of("approved", true)).assertStatus(403);
        assertThat(list(call(HttpMethod.GET, "/api/organizers/verification-requests?size=100", admin, null)
                .assertStatus(200).json().get("content")))
                .anySatisfy(r -> assertThat(r.get("id").asLong()).isEqualTo(profile));

        JsonNode rejected = call(HttpMethod.POST, "/api/organizers/" + profile + "/verification", admin,
                Map.of("approved", false, "note", "Blurry document")).assertStatus(200).json();
        assertThat(rejected.get("verificationStatus").asString()).isEqualTo("REJECTED");
        assertThat(notifications(organizer).get(0).get("body").asString()).contains("Blurry document");

        call(HttpMethod.POST, "/api/organizers/profile/verification", organizer, null).assertStatus(200); // may try again
    }

    @Test
    void onlyTheLastFourIbanCharactersAreKept() {
        String organizer = organizer();

        call(HttpMethod.PUT, "/api/organizers/profile/payout-account", organizer,
                Map.of("bankName", "Bank", "accountHolder", "Holder", "iban", "not-an-iban")).assertStatus(400);
        Response saved = call(HttpMethod.PUT, "/api/organizers/profile/payout-account", organizer,
                Map.of("bankName", "Bank", "accountHolder", "Holder", "iban", IBAN)).assertStatus(200);

        assertThat(saved.json().get("ibanLast4").asString()).isEqualTo("5678");
        assertThat(saved.json().get("simulated").asBoolean()).isTrue();
        assertThat(saved.text()).doesNotContain(IBAN);
    }

    @Test
    void adminCanStopPaidSalesWithoutTouchingFreeTicketsOrSoldOnes() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long paid = ticketTypeWithoutActivation(organizer, event, 5000, 10);
        long free = ticketTypeWithoutActivation(organizer, event, 0, 10);
        String admin = admin();
        activateOverHttp(organizer, event, admin);
        String attendee = register();
        long paidOrder = createOrder(attendee, event, paid, 1).get("id").asLong();
        pay(attendee, paidOrder).assertStatus(200);
        long pendingOrder = createOrder(attendee, event, paid, 1).get("id").asLong();

        call(HttpMethod.POST, "/api/events/" + event + "/activation/suspend", organizer, null).assertStatus(403);
        JsonNode suspended = call(HttpMethod.POST, "/api/events/" + event + "/activation/suspend", admin,
                Map.of("reason", "Suspected fraud")).assertStatus(200).json();

        assertThat(suspended.get("status").asString()).isEqualTo("SUSPENDED");
        call(HttpMethod.POST, "/api/orders", attendee, orderBody(event, paid, 1)).assertStatus(400);
        pay(attendee, pendingOrder).assertStatus(400);
        createOrder(attendee, event, free, 1);
        assertThat(scan(organizer, event, tickets(attendee).stream()
                .filter(t -> t.get("ticketTypeId").asLong() == paid).findFirst().orElseThrow().get("code").asString())
                .get("result").asString()).isEqualTo("CHECKED_IN");
        assertThat(notificationTypes(organizer)).contains("PAID_SALES_STATUS");

        call(HttpMethod.POST, "/api/events/" + event + "/activation/unsuspend", admin, null).assertStatus(200);
        createOrder(attendee, event, paid, 1);
        assertThat(list(call(HttpMethod.GET, "/api/admin/paid-sales-activations?status=ACTIVE&size=100", admin, null)
                .assertStatus(200).json().get("content")))
                .anySatisfy(a -> assertThat(a.get("eventId").asLong()).isEqualTo(event));
    }

    // promo codes //

    @Test
    void percentDiscountIsCalculatedByTheServerAndFlowsIntoSalesFigures() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 2500, 10);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 20));
        String attendee = register();

        JsonNode quote = call(HttpMethod.POST, "/api/orders/quote", attendee, orderWithPromo(event, type, 2, code(campaign)))
                .assertStatus(200).json();
        assertThat(quote.get("subtotalAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(quote.get("discountAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(quote.get("totalAmount").decimalValue()).isEqualByComparingTo("4000");
        assertThat(campaignNow(organizer, event, campaign).get("pendingRedemptions").asLong()).isZero(); // a quote reserves nothing

        // Typed in lower case: codes are not case sensitive.
        JsonNode order = call(HttpMethod.POST, "/api/orders", attendee,
                orderWithPromo(event, type, 2, code(campaign).toLowerCase())).assertStatus(201).json();
        assertThat(order.get("promoCode").asString()).isEqualTo(code(campaign));
        assertThat(order.get("subtotalAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(order.get("discountAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("4000");
        pay(attendee, order.get("id").asLong()).assertStatus(200);

        assertThat(payments(attendee, order.get("id").asLong()).get(0).get("processingFee").decimalValue()).isEqualByComparingTo("100");
        JsonNode sales = call(HttpMethod.GET, "/api/organizer/events/" + event + "/sales", organizer, null).assertStatus(200).json();
        assertThat(sales.get("revenue").decimalValue()).isEqualByComparingTo("5000");
        assertThat(sales.get("discounts").decimalValue()).isEqualByComparingTo("1000");
        assertThat(sales.get("netRevenue").decimalValue()).isEqualByComparingTo("4000");
        assertThat(sales.get("estimatedPayout").decimalValue()).isEqualByComparingTo("3900");

        JsonNode stats = campaignNow(organizer, event, campaign);
        assertThat(stats.get("redemptions").asLong()).isEqualTo(1);
        assertThat(stats.get("ticketsSold").asLong()).isEqualTo(2);
        assertThat(stats.get("grossRevenue").decimalValue()).isEqualByComparingTo("5000");
        assertThat(stats.get("discountAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(stats.get("netRevenue").decimalValue()).isEqualByComparingTo("4000");
    }

    @Test
    void fixedDiscountNeverGoesBelowZeroAndAFullyDiscountedOrderIsConfirmedAtOnce() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 2000, 10);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "FIXED", "discountValue", 3000));
        String attendee = register();

        JsonNode order = call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, code(campaign)))
                .assertStatus(201).json();

        assertThat(order.get("discountAmount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(order.get("status").asString()).isEqualTo("PAID");
        assertThat(tickets(attendee)).hasSize(1);
    }

    @Test
    void discountAppliesOnlyToTheCampaignsTicketTypes() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long covered = ticketType(organizer, event, 1000, 10);
        long notCovered = ticketType(organizer, event, 3000, 10);
        JsonNode campaign = campaign(organizer, event,
                Map.of("discountType", "PERCENT", "discountValue", 50, "ticketTypeIds", List.of(covered)));
        String attendee = register();

        Map<String, Object> mixed = new HashMap<>(Map.of("eventId", event, "promoCode", code(campaign), "items", List.of(
                Map.of("ticketTypeId", covered, "quantity", 1), Map.of("ticketTypeId", notCovered, "quantity", 1))));
        assertThat(call(HttpMethod.POST, "/api/orders", attendee, mixed).assertStatus(201).json().get("discountAmount").decimalValue())
                .isEqualByComparingTo("500");

        assertThat(call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, notCovered, 1, code(campaign)))
                .assertStatus(400).text()).contains("does not apply");
    }

    @Test
    void unusableCodesAreRejectedWithAClearMessage() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long otherEvent = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 50);
        String attendee = register();

        JsonNode expired = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10,
                "validUntil", LocalDateTime.now().minusDays(1).toString()));
        JsonNode future = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10,
                "validFrom", LocalDateTime.now().plusDays(1).toString()));
        JsonNode disabled = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10));
        call(HttpMethod.POST, campaignPath(event, disabled) + "/disable", organizer, null).assertStatus(200);
        JsonNode forOtherEvent = campaign(organizer, otherEvent, Map.of("discountType", "PERCENT", "discountValue", 10));

        assertThat(promoError(attendee, event, type, code(expired))).contains("expired");
        assertThat(promoError(attendee, event, type, code(future))).contains("not active yet");
        assertThat(promoError(attendee, event, type, code(disabled))).contains("disabled");
        assertThat(promoError(attendee, event, type, code(forOtherEvent))).contains("not valid for this event");
        assertThat(promoError(attendee, event, type, "NOSUCHCODE")).contains("not valid for this event");
        assertThat(tickets(attendee)).isEmpty();

        call(HttpMethod.POST, campaignPath(event, disabled) + "/enable", organizer, null).assertStatus(200);
        call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, code(disabled))).assertStatus(201);
        assertThat(auditActions(event)).contains("PROMO_CAMPAIGN_CREATED", "PROMO_CAMPAIGN_DISABLED", "PROMO_CAMPAIGN_ENABLED");
    }

    @Test
    void invalidCampaignsCannotBeCreated() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long foreignType = ticketType(organizer, publishedEvent(organizer), 1000, 10);
        String path = "/api/events/" + event + "/campaigns";

        call(HttpMethod.POST, path, organizer, Map.of("name", "Too much", "discountType", "PERCENT", "discountValue", 101)).assertStatus(400);
        call(HttpMethod.POST, path, organizer, Map.of("name", "Nothing", "discountType", "FIXED", "discountValue", 0)).assertStatus(400);
        call(HttpMethod.POST, path, organizer, Map.of("name", "Backwards", "discountType", "FIXED", "discountValue", 5,
                "validFrom", LocalDateTime.now().plusDays(2).toString(), "validUntil", LocalDateTime.now().plusDays(1).toString())).assertStatus(400);
        call(HttpMethod.POST, path, organizer, Map.of("name", "Foreign", "discountType", "FIXED", "discountValue", 5,
                "ticketTypeIds", List.of(foreignType))).assertStatus(400);
        call(HttpMethod.POST, path, organizer(), Map.of("name", "Not mine", "discountType", "FIXED", "discountValue", 5)).assertStatus(403);
    }

    @Test
    void concurrentOrdersCannotExceedTheRedemptionLimit() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 50);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10, "maxRedemptions", 3));
        String attendee = register();

        List<Integer> statuses = concurrently(12,
                () -> call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, code(campaign))).status());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(9);
        assertThat(promoError(attendee, event, type, code(campaign))).contains("redemption limit");
    }

    @Test
    void anUnpaidOrderHoldsARedemptionUntilItIsCancelled() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 50);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10, "maxRedemptions", 1));
        String attendee = register();
        long orderId = call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, code(campaign)))
                .assertStatus(201).json().get("id").asLong();
        assertThat(campaignNow(organizer, event, campaign).get("remainingRedemptions").asLong()).isZero();
        assertThat(promoError(register(), event, type, code(campaign))).contains("redemption limit");

        call(HttpMethod.POST, "/api/orders/" + orderId + "/cancel", attendee, null).assertStatus(200);

        call(HttpMethod.POST, "/api/orders", register(), orderWithPromo(event, type, 1, code(campaign))).assertStatus(201);
    }

    @Test
    void campaignQrIsATrackableLinkThatAppliesTheCodeButNeverAdmitsAnyone() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 25));

        Response qr = call(HttpMethod.GET, campaignPath(event, campaign) + "/qr", organizer, null).assertStatus(200);
        assertThat(qr.contentType()).isEqualTo(MediaType.IMAGE_PNG);
        BufferedImage framed = ImageIO.read(new ByteArrayInputStream(qr.body()));
        int frame = QrCodeGenerator.CAMPAIGN_FRAME_PX;
        String link = decodeQr(framed.getSubimage(frame, frame, framed.getWidth() - 2 * frame, framed.getHeight() - 2 * frame));

        assertThat(link).isEqualTo(campaign.get("campaignUrl").asString())
                .contains("/events/" + event + "?promo=")
                .doesNotContain("25", code(campaign));
        // The corner of an admission QR is white; a campaign QR has a coloured frame.
        assertThat(framed.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x1E3A8A);

        String token = link.substring(link.indexOf("promo=") + "promo=".length());
        JsonNode preview = call(HttpMethod.GET, "/api/events/" + event + "/promo?token=" + token, null, null).assertStatus(200).json();
        assertThat(preview.get("code").asString()).isEqualTo(code(campaign));
        assertThat(preview.get("discountValue").decimalValue()).isEqualByComparingTo("25");

        JsonNode rejected = scan(organizer, event, link);
        assertThat(rejected.get("result").asString()).isEqualTo("INVALID");
        assertThat(rejected.get("message").asString()).contains("promotional");

        String attendee = register();
        long orderId = call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, preview.get("code").asString()))
                .assertStatus(201).json().get("id").asLong();
        pay(attendee, orderId).assertStatus(200);
        assertThat(scan(organizer, event, tickets(attendee).get(0).get("code").asString()).get("result").asString()).isEqualTo("CHECKED_IN");
    }

    @Test
    void campaignDetailsAreOnlyForTheOrganizerAndHiddenEventsRevealNothing() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        JsonNode campaign = campaign(organizer, event, Map.of("discountType", "PERCENT", "discountValue", 10));
        String path = campaignPath(event, campaign);

        call(HttpMethod.GET, path, null, null).assertStatus(404);
        call(HttpMethod.GET, path + "/qr", null, null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + event + "/campaigns", organizer(), null).assertStatus(404);
        call(HttpMethod.GET, path, admin(), null).assertStatus(200);

        call(HttpMethod.POST, "/api/events/" + event + "/unpublish", organizer, null).assertStatus(200);
        call(HttpMethod.GET, "/api/events/" + event + "/promo?code=" + code(campaign), null, null).assertStatus(404);
    }

    // helpers //

    private JsonNode activation(String user, long event) {
        return call(HttpMethod.GET, "/api/events/" + event + "/activation", user, null).assertStatus(200).json();
    }

    private Response activationStep(String user, long event, String step) {
        return call(HttpMethod.POST, "/api/events/" + event + "/activation/" + step, user, null);
    }

    private List<String> doneItems(JsonNode activation) {
        return list(activation.get("checklist")).stream()
                .filter(item -> item.get("done").asBoolean())
                .map(item -> item.get("key").asString())
                .toList();
    }

    private void verifyOrganizer(String organizer, String admin) {
        long profile = call(HttpMethod.GET, "/api/organizers/profile", organizer, null).assertStatus(200).json().get("id").asLong();
        call(HttpMethod.POST, "/api/organizers/profile/verification", organizer, null).assertStatus(200);
        call(HttpMethod.POST, "/api/organizers/" + profile + "/verification", admin, Map.of("approved", true)).assertStatus(200);
    }

    private void registerPayoutAccount(String organizer) {
        call(HttpMethod.PUT, "/api/organizers/profile/payout-account", organizer,
                Map.of("bankName", "Demo Bank", "accountHolder", "Test Organizer", "iban", IBAN)).assertStatus(200);
    }

    private void activateOverHttp(String organizer, long event, String admin) {
        verifyOrganizer(organizer, admin);
        registerPayoutAccount(organizer);
        activationStep(organizer, event, "pay-fee").assertStatus(200);
        activationStep(organizer, event, "accept-terms").assertStatus(200);
        activationStep(organizer, event, "activate").assertStatus(200);
    }

    private JsonNode campaign(String organizer, long event, Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("name", "Test campaign");
        return call(HttpMethod.POST, "/api/events/" + event + "/campaigns", organizer, body).assertStatus(201).json();
    }

    private String campaignPath(long event, JsonNode campaign) {
        return "/api/events/" + event + "/campaigns/" + campaign.get("id").asLong();
    }

    private JsonNode campaignNow(String organizer, long event, JsonNode campaign) {
        return call(HttpMethod.GET, campaignPath(event, campaign), organizer, null).assertStatus(200).json();
    }

    private String code(JsonNode campaign) {
        return campaign.get("code").asString();
    }

    private Map<String, Object> orderWithPromo(long event, long type, int quantity, String promoCode) {
        Map<String, Object> body = new HashMap<>(orderBody(event, type, quantity));
        body.put("promoCode", promoCode);
        return body;
    }

    private String promoError(String attendee, long event, long type, String promoCode) {
        return call(HttpMethod.POST, "/api/orders", attendee, orderWithPromo(event, type, 1, promoCode)).assertStatus(400).text();
    }
}
