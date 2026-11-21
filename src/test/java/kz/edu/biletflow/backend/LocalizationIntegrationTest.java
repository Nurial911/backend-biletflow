package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.OutgoingMail;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// SRS section 7: what the backend writes itself (emails, notifications, PDF tickets, calendar files, scan results)
// is in each user's language, Russian by default, Kazakh or English on request.
class LocalizationIntegrationTest extends ApiIntegrationTest {
    @Autowired
    private CapturingMailGateway mailbox;

    @Test
    void newAccountsAreRussianUnlessTheyAskForAnotherLanguage() {
        assertThat(languageOf(registerWith(null, null))).isEqualTo("RU");
        assertThat(languageOf(registerWith(null, "de-DE,de;q=0.9"))).isEqualTo("RU");
        assertThat(languageOf(registerWith(null, "kk-KZ,kk;q=0.9,ru;q=0.8"))).isEqualTo("KK");
        assertThat(languageOf(registerWith(null, "en-US"))).isEqualTo("EN");
        // A language in the body wins over the header.
        assertThat(languageOf(registerWith("RU", "kk-KZ"))).isEqualTo("RU");
    }

    @Test
    void theConfirmationEmailIsInTheNewAccountsLanguage() {
        String email = "user-" + UUID.randomUUID() + "@test.kz";
        registerBody(Map.of("email", email, "password", PASSWORD), "kk");

        await().atMost(Duration.ofSeconds(10)).until(() -> !mailbox.sentTo(email).isEmpty());
        OutgoingMail mail = mailbox.sentTo(email).get(0);
        assertThat(mail.subject()).isEqualTo("BiletFlow-тағы электрондық пошта мекенжайыңызды растаңыз");
        assertThat(mail.text()).contains("24 сағат", "token=");
    }

    @Test
    void usersCanChangeTheirLanguage() {
        String user = register();
        assertThat(call(HttpMethod.PUT, "/api/users/me/credentials", user, Map.of("language", "KK"))
                .assertStatus(200).json().get("language").asString()).isEqualTo("KK");
        assertThat(languageOf(user)).isEqualTo("KK");
        call(HttpMethod.PUT, "/api/users/me/credentials", user, Map.of("language", "DE")).assertStatus(400);
        assertThat(languageOf(user)).isEqualTo("KK");
    }

    @Test
    void eachTicketHolderReadsTheSameNewsInTheirOwnLanguage() throws IOException {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String russian = registerIn("RU");
        String kazakh = registerIn("KK");
        long kazakhOrder = createOrder(kazakh, event, type, 1).get("id").asLong();
        createOrder(russian, event, type, 1);

        JsonNode ruConfirmation = latestNotification(russian);
        assertThat(ruConfirmation.get("title").asString()).isEqualTo("Ваши билеты на «Test event»");
        assertThat(ruConfirmation.get("body").asString()).contains("Заказ №", "(время Алматы)", "Итого: 0.00 KZT");
        JsonNode kkConfirmation = latestNotification(kazakh);
        assertThat(kkConfirmation.get("title").asString()).isEqualTo("«Test event» іс-шарасына билеттеріңіз");
        assertThat(kkConfirmation.get("body").asString()).contains("№" + kazakhOrder + " тапсырыс расталды", "Алматы уақыты");

        // The emailed PDF ticket speaks the buyer's language too.
        deliverQueuedEmails();
        OutgoingMail mail = mailbox.sentTo(emailOf(kazakh)).stream()
                .filter(m -> m.subject().equals("«Test event» іс-шарасына билеттеріңіз")).findFirst().orElseThrow();
        OutgoingMail.Attachment pdf = mail.attachments().stream()
                .filter(a -> a.contentType().equals("application/pdf")).findFirst().orElseThrow();
        assertThat(textOf(pdf.content())).contains("Күні мен уақыты", "Билет нөмірі", "Жарамды", "(Алматы уақыты)");

        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);

        assertThat(latestNotification(russian).get("title").asString()).isEqualTo("Мероприятие «Test event» отменено");
        assertThat(latestNotification(kazakh).get("title").asString()).isEqualTo("«Test event» іс-шарасы болдырылмады");
        deliverQueuedEmails();
        OutgoingMail cancellation = mailbox.sentTo(emailOf(kazakh)).stream()
                .filter(m -> m.subject().equals("«Test event» іс-шарасы болдырылмады")).findFirst().orElseThrow();
        String ics = cancellation.attachments().stream().filter(a -> a.filename().endsWith(".ics")).findFirst()
                .map(a -> unfold(new String(a.content(), StandardCharsets.UTF_8))).orElseThrow();
        assertThat(ics).contains("SUMMARY:Болдырылмады: Test event", "Ұйымдастырушы бұл іс-шараны болдырмады");
    }

    @Test
    void scanResultsAndDownloadedTicketsFollowTheReadersLanguage() throws IOException {
        String organizer = organizer();
        call(HttpMethod.PUT, "/api/users/me/credentials", organizer, Map.of("language", "RU")).assertStatus(200);
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String kazakh = registerIn("KK");
        createOrder(kazakh, event, type, 1);
        JsonNode ticket = tickets(kazakh).get(0);

        byte[] pdf = call(HttpMethod.GET, "/api/tickets/" + ticket.get("id").asLong() + "/pdf", kazakh, null)
                .assertStatus(200).body();
        assertThat(textOf(pdf)).contains("Қатысушы", "Жарамды").doesNotContain("Attendee");

        // The organizer scans in Russian; the result value itself stays the same for every language.
        JsonNode first = scan(organizer, event, ticket.get("code").asString());
        assertThat(first.get("result").asString()).isEqualTo("CHECKED_IN");
        assertThat(first.get("message").asString()).isEqualTo("Вход отмечен");
        JsonNode second = scan(organizer, event, ticket.get("code").asString());
        assertThat(second.get("result").asString()).isEqualTo("ALREADY_USED");
        assertThat(second.get("message").asString()).isEqualTo("Этот билет уже использован");
        assertThat(scan(organizer, event, "no-such-code").get("message").asString()).isEqualTo("Неизвестный код билета");
    }

    @Test
    void anonymousCalendarFilesFollowAcceptLanguage() {
        long event = publishedEvent(organizer());
        String path = "/api/events/" + event + "/calendar.ics";

        assertThat(unfold(call(HttpMethod.GET, path, null, null, "kk-KZ").assertStatus(200).text()))
                .contains("Іс-шара беті: ");
        assertThat(unfold(call(HttpMethod.GET, path, null, null, "en").assertStatus(200).text()))
                .contains("Event page: ");
        assertThat(unfold(call(HttpMethod.GET, path, null, null, null).assertStatus(200).text()))
                .contains("Страница мероприятия: ");
    }

    // helpers //

    private String registerIn(String language) {
        return registerWith(language, null);
    }

    private String registerWith(String language, String acceptLanguage) {
        Map<String, Object> body = new HashMap<>(Map.of("email", "user-" + UUID.randomUUID() + "@test.kz",
                "password", PASSWORD));
        if (language != null) {
            body.put("language", language);
        }
        return registerBody(body, acceptLanguage);
    }

    private String registerBody(Map<String, Object> body, String acceptLanguage) {
        return call(HttpMethod.POST, "/api/auth/register", null, body, acceptLanguage)
                .assertStatus(201).json().get("accessToken").asString();
    }

    private String languageOf(String user) {
        return call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(200).json().get("language").asString();
    }

    private JsonNode latestNotification(String user) {
        List<JsonNode> all = notifications(user);
        assertThat(all).isNotEmpty();
        return all.get(0);
    }

    private static String unfold(String ics) {
        return ics.replace("\r\n ", "");
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }
}
