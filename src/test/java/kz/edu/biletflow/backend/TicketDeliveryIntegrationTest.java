package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.OutgoingMail;
import kz.edu.biletflow.backend.services.impl.TicketPdfGenerator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// PDF tickets, emails, in-app notifications and the emailed account links.
class TicketDeliveryIntegrationTest extends ApiIntegrationTest {
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");
    private static final float A4_HEIGHT = 842;
    private static final float A4_WIDTH = 595;

    @Autowired
    private CapturingMailGateway mailbox;

    // PDF tickets //

    @Test
    void pdfTicketShowsEverythingTheSrsRequiresInKazakhAndRussianButNoPaymentData() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer, "Қазақ әні кеші: Өңір ұлы Һ", "Концертный зал Ғұмыр");
        long type = ticketType(organizer, event, 2500, 10);
        String attendee = register();
        Map<String, Object> body = new HashMap<>(orderBody(event, type, 1));
        body.put("attendeeName", "Әлия Нұрланова");
        long orderId = call(HttpMethod.POST, "/api/orders", attendee, body).assertStatus(201).json().get("id").asLong();
        pay(attendee, orderId).assertStatus(200);
        JsonNode ticket = tickets(attendee).get(0);

        Response pdf = call(HttpMethod.GET, "/api/tickets/" + ticket.get("id").asLong() + "/pdf", attendee, null)
                .assertStatus(200);

        assertThat(pdf.contentType()).isEqualTo(MediaType.APPLICATION_PDF);
        String text = textOf(pdf.body());
        assertThat(text).contains("Қазақ әні кеші: Өңір ұлы Һ", "Концертный зал Ғұмыр", "Test street 1, Astana",
                "Standard", "Әлия Нұрланова", ticket.get("code").asString(), "Valid", "Almaty time");
        assertThat(text).doesNotContain("KZT", "2500", "SIM-");
    }

    @Test
    void printedQrScansInGrayscaleAndIsTheSameAdmissionAsTheDigitalTicket() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        JsonNode ticket = tickets(attendee).get(0);
        byte[] pdf = call(HttpMethod.GET, "/api/tickets/" + ticket.get("id").asLong() + "/pdf", attendee, null)
                .assertStatus(200).body();

        String decoded = decodeQr(printedQr(pdf, 0));

        assertThat(decoded).isEqualTo(ticket.get("code").asString());
        assertThat(scan(organizer, event, decoded).get("result").asString()).isEqualTo("CHECKED_IN");
        assertThat(scan(organizer, event, ticket.get("code").asString()).get("result").asString()).isEqualTo("ALREADY_USED");
    }

    @Test
    void pdfIsPrivateAndRefusedForCancelledTickets() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        long ticketId = tickets(attendee).get(0).get("id").asLong();

        call(HttpMethod.GET, "/api/tickets/" + ticketId + "/pdf", register(), null).assertStatus(404);
        call(HttpMethod.GET, "/api/tickets/" + ticketId + "/pdf", null, null).assertStatus(401);

        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);

        call(HttpMethod.GET, "/api/tickets/" + ticketId + "/pdf", attendee, null).assertStatus(400);
    }

    // order and event emails //

    @Test
    void confirmedOrderIsEmailedWithOnePdfPagePerTicketAndShownInApp() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        String email = emailOf(attendee);

        createOrder(attendee, event, type, 2);
        deliverQueuedEmails();

        OutgoingMail mail = onlyMailWithSubject(email, "Your tickets for Test event");
        assertThat(mail.attachment()).isNotNull();
        try (PDDocument pdf = Loader.loadPDF(mail.attachment().content())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
        }
        for (int page = 0; page < 2; page++) {
            assertThat(decodeQr(printedQr(mail.attachment().content(), page)))
                    .isEqualTo(tickets(attendee).get(page).get("code").asString());
        }
        assertThat(mail.text()).doesNotContain("demonstration payment");

        List<JsonNode> inApp = notifications(attendee);
        assertThat(inApp).hasSize(1);
        assertThat(inApp.get(0).get("type").asString()).isEqualTo("ORDER_CONFIRMED");
        assertThat(unread(attendee)).isEqualTo(1);

        long id = inApp.get(0).get("id").asLong();
        call(HttpMethod.POST, "/api/notifications/" + id + "/read", register(), null).assertStatus(404);
        call(HttpMethod.POST, "/api/notifications/" + id + "/read", attendee, null).assertStatus(200);
        assertThat(unread(attendee)).isZero();
    }

    @Test
    void paidOrderEmailSaysTheMoneyIsNotRealAndAFailedPaymentIsReported() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        String attendee = register();
        long orderId = createOrder(attendee, event, type, 1).get("id").asLong();

        call(HttpMethod.POST, "/api/orders/" + orderId + "/pay", attendee, Map.of("simulatedOutcome", "DECLINED"))
                .assertStatus(402);
        assertThat(notificationTypes(attendee)).containsExactly("PAYMENT_FAILED");

        pay(attendee, orderId).assertStatus(200);
        deliverQueuedEmails();

        assertThat(notificationTypes(attendee)).containsExactly("ORDER_CONFIRMED", "PAYMENT_FAILED");
        assertThat(onlyMailWithSubject(emailOf(attendee), "Your tickets for Test event").text())
                .contains("demonstration payment");
    }

    @Test
    void ticketHoldersHearAboutMovesAndCancellationsButNotOtherEdits() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String holder = register();
        createOrder(holder, event, type, 2);
        String bystander = register();

        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("description", "New words")).assertStatus(200);
        assertThat(notificationTypes(holder)).containsExactly("ORDER_CONFIRMED");

        call(HttpMethod.PUT, "/api/events/" + event, organizer,
                Map.of("startTime", LocalDateTime.now().plusDays(40).toString())).assertStatus(200);
        assertThat(notificationTypes(holder)).containsExactly("EVENT_UPDATED", "ORDER_CONFIRMED");

        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);
        assertThat(notificationTypes(holder)).containsExactly("EVENT_CANCELLED", "EVENT_UPDATED", "ORDER_CONFIRMED");
        assertThat(notificationTypes(bystander)).isEmpty();
    }

    @Test
    void failedEmailIsRetriedLaterAndNeverBreaksTheOrder() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        String email = emailOf(attendee);
        deliverQueuedEmails();

        createOrder(attendee, event, type, 1);
        mailbox.failNextSend();
        deliverQueuedEmails();

        assertThat(mailsWithSubject(email, "Your tickets for Test event")).isEmpty();
        assertThat(tickets(attendee)).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_attempts FROM notifications WHERE recipient_email = ? AND email_status = 'PENDING'", Integer.class, email))
                .isEqualTo(1);

        deliverQueuedEmails();
        assertThat(mailsWithSubject(email, "Your tickets for Test event")).isEmpty(); // not due yet

        jdbcTemplate.update("UPDATE notifications SET next_attempt_at = ? WHERE recipient_email = ?",
                LocalDateTime.now().minusSeconds(1), email);
        deliverQueuedEmails();
        assertThat(mailsWithSubject(email, "Your tickets for Test event")).hasSize(1);
    }

    // account links //

    @Test
    void registrationEmailVerifiesTheAddressOnce() {
        String email = "verify-" + System.nanoTime() + "@test.kz";
        String user = register(email);
        assertThat(verified(user)).isFalse();

        String token = tokenFrom(email, "Confirm your BiletFlow email address");
        call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("token", token)).assertStatus(204);

        assertThat(verified(user)).isTrue();
        call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("token", token)).assertStatus(400);
    }

    @Test
    void passwordResetChangesThePasswordOnceAndStoresOnlyAHash() {
        String email = "reset-" + System.nanoTime() + "@test.kz";
        register(email);

        call(HttpMethod.POST, "/api/auth/forgot-password", null, Map.of("email", email)).assertStatus(204);
        String token = tokenFrom(email, "Reset your BiletFlow password");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_tokens WHERE token_hash = ?", Long.class, token)).isZero();
        call(HttpMethod.POST, "/api/auth/reset-password", null, Map.of("token", token, "newPassword", "short")).assertStatus(400);
        call(HttpMethod.POST, "/api/auth/reset-password", null, Map.of("token", token, "newPassword", "NewPassword456!")).assertStatus(204);

        login(email, "NewPassword456!");
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", PASSWORD)).assertStatus(401);
        call(HttpMethod.POST, "/api/auth/reset-password", null, Map.of("token", token, "newPassword", "Another789!")).assertStatus(400);
    }

    @Test
    void forgotPasswordRevealsNothingAboutUnknownAddressesAndIsThrottled() throws Exception {
        String unknown = "nobody-" + System.nanoTime() + "@test.kz";
        call(HttpMethod.POST, "/api/auth/forgot-password", null, Map.of("email", unknown)).assertStatus(204);

        String email = "throttle-" + System.nanoTime() + "@test.kz";
        register(email);
        call(HttpMethod.POST, "/api/auth/forgot-password", null, Map.of("email", email)).assertStatus(204);
        call(HttpMethod.POST, "/api/auth/forgot-password", null, Map.of("email", email)).assertStatus(204);
        tokenFrom(email, "Reset your BiletFlow password");
        Thread.sleep(300); // long enough for a second email to arrive if one had been sent

        assertThat(mailsWithSubject(email, "Reset your BiletFlow password")).hasSize(1);
        assertThat(mailbox.sentTo(unknown)).isEmpty();
    }

    @Test
    void aVerificationLinkCannotResetAPasswordOrTheOtherWayRound() {
        String email = "purpose-" + System.nanoTime() + "@test.kz";
        register(email);
        String verification = tokenFrom(email, "Confirm your BiletFlow email address");

        call(HttpMethod.POST, "/api/auth/reset-password", null,
                Map.of("token", verification, "newPassword", "NewPassword456!")).assertStatus(400);
        login(email, PASSWORD);
    }

    @Test
    void changingTheEmailAddressRequiresConfirmingTheNewOne() {
        String email = "before-" + System.nanoTime() + "@test.kz";
        String newEmail = "after-" + System.nanoTime() + "@test.kz";
        String user = register(email);
        call(HttpMethod.POST, "/api/auth/verify-email", null,
                Map.of("token", tokenFrom(email, "Confirm your BiletFlow email address"))).assertStatus(204);

        call(HttpMethod.PUT, "/api/users/me/credentials", user, Map.of("email", newEmail)).assertStatus(200);

        assertThat(verified(user)).isFalse();
        call(HttpMethod.POST, "/api/auth/verify-email", null,
                Map.of("token", tokenFrom(newEmail, "Confirm your BiletFlow email address"))).assertStatus(204);
        assertThat(verified(user)).isTrue();
    }

    // helpers //

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    // Prints the page in grayscale at a usual printer resolution and cuts out the QR area.
    private BufferedImage printedQr(byte[] pdf, int page) throws IOException {
        float dpi = 300;
        float scale = dpi / 72;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(page, dpi, ImageType.GRAY);
            int x = Math.round((A4_WIDTH - TicketPdfGenerator.QR_SIZE) / 2 * scale);
            int y = Math.round((A4_HEIGHT - TicketPdfGenerator.QR_BOTTOM - TicketPdfGenerator.QR_SIZE) * scale);
            int size = Math.round(TicketPdfGenerator.QR_SIZE * scale);
            return image.getSubimage(x, y, size, size);
        }
    }

    private long unread(String user) {
        return call(HttpMethod.GET, "/api/notifications/unread-count", user, null).assertStatus(200).json().get("unread").asLong();
    }

    private boolean verified(String user) {
        return call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(200).json().get("verificationStatus").asBoolean();
    }

    private List<OutgoingMail> mailsWithSubject(String email, String subject) {
        return mailbox.sentTo(email).stream().filter(mail -> mail.subject().equals(subject)).toList();
    }

    private OutgoingMail onlyMailWithSubject(String email, String subject) {
        List<OutgoingMail> mails = mailsWithSubject(email, subject);
        assertThat(mails).hasSize(1);
        return mails.get(0);
    }

    // Account emails are sent off the request thread, so they arrive a moment later.
    private String tokenFrom(String email, String subject) {
        await().atMost(java.time.Duration.ofSeconds(10)).until(() -> !mailsWithSubject(email, subject).isEmpty());
        List<OutgoingMail> mails = mailsWithSubject(email, subject);
        Matcher matcher = TOKEN.matcher(mails.get(mails.size() - 1).text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
