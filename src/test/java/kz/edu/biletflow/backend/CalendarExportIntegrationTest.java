package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.OutgoingMail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Calendar export (SRS 4.11): .ics files, calendar links, and the files attached to emails.
class CalendarExportIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private CapturingMailGateway mailbox;

    @Test
    void icsFileHasTheEventDetailsInItsTimeZone() {
        String organizer = organizer();
        long event = publishedEvent(organizer, "Jazz; night, live", "Grand Hall");
        LocalDateTime start = LocalDateTime.of(LocalDateTime.now().getYear() + 1, 3, 14, 19, 30);
        String description = "Line one, with comma; and semicolon\nЖаңа жыл кеші — " + "ұзақ сипаттама ".repeat(10);
        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of(
                "description", description,
                "startTime", start.toString(),
                "endTime", start.plusHours(3).toString())).assertStatus(200);

        Response response = call(HttpMethod.GET, "/api/events/" + event + "/calendar.ics", null, null).assertStatus(200);
        assertThat(response.contentType().toString()).startsWith("text/calendar");
        String raw = response.text();

        // Every content line ends with CRLF and is at most 75 octets; longer ones are folded.
        assertThat(raw).endsWith("END:VCALENDAR\r\n");
        for (String physical : raw.split("\r\n")) {
            assertThat(physical.getBytes(StandardCharsets.UTF_8).length).as(physical).isLessThanOrEqualTo(75);
        }
        String ics = unfold(raw);
        String date = compact(start);
        assertThat(ics).contains(
                "METHOD:PUBLISH",
                "TZID:Asia/Almaty",
                "TZOFFSETTO:+0500",
                "UID:event-" + event + "@localhost",
                "SEQUENCE:1", // raised by the update
                "DTSTART;TZID=Asia/Almaty:" + date + "T193000",
                "DTEND;TZID=Asia/Almaty:" + date + "T223000",
                "SUMMARY:Jazz\\; night\\, live",
                "DESCRIPTION:Line one\\, with comma\\; and semicolon\\nЖаңа жыл кеші",
                "\\n\\nEvent page: http://localhost:5173/events/" + event,
                "LOCATION:Grand Hall\\, Test street 1\\, Astana",
                "URL:http://localhost:5173/events/" + event,
                "STATUS:CONFIRMED");
    }

    @Test
    void eventWithoutEndTimeLastsUntilTheEndOfItsDay() {
        long event = publishedEvent(organizer());
        String ics = ics(null, event);
        LocalDateTime start = LocalDateTime.parse(field(ics, "DTSTART;TZID=Asia/Almaty:"),
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        assertThat(field(ics, "DTEND;TZID=Asia/Almaty:")).isEqualTo(compact(start.plusDays(1)) + "T000000");
    }

    @Test
    void calendarLinksPointToTheSameEntry() {
        String organizer = organizer();
        long event = publishedEvent(organizer, "Folk evening", "Small Hall");
        LocalDateTime start = LocalDateTime.of(LocalDateTime.now().getYear() + 1, 5, 2, 18, 0);
        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("startTime", start.toString(),
                "endTime", start.plusHours(2).toString())).assertStatus(200);
        String date = compact(start);

        JsonNode links = call(HttpMethod.GET, "/api/events/" + event + "/calendar", null, null).assertStatus(200).json();
        assertThat(links.get("cancelled").asBoolean()).isFalse();
        assertThat(links.get("sequence").asInt()).isEqualTo(1);
        assertThat(links.get("icsUrl").asString()).endsWith("/api/events/" + event + "/calendar.ics");
        assertThat(links.get("googleCalendarUrl").asString())
                .startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("text=Folk%20evening", "dates=" + date + "T180000/" + date + "T200000", "ctz=Asia%2FAlmaty",
                        "location=Small%20Hall%2C%20Test%20street%201%2C%20Astana");
        String isoDate = start.toLocalDate().toString();
        assertThat(links.get("outlookUrl").asString())
                .startsWith("https://outlook.live.com/calendar/0/action/compose?")
                .contains("subject=Folk%20evening", "startdt=" + isoDate + "T18%3A00%3A00%2B05%3A00",
                        "enddt=" + isoDate + "T20%3A00%3A00%2B05%3A00");
        assertThat(links.get("office365Url").asString()).startsWith("https://outlook.office.com/calendar/0/action/compose?");
    }

    @Test
    void calendarFollowsEventVisibility() {
        String organizer = organizer();
        long draft = draftEvent(organizer, "Draft", "Hall");
        String path = "/api/events/" + draft + "/calendar.ics";
        call(HttpMethod.GET, path, null, null).assertStatus(404);
        call(HttpMethod.GET, path, register(), null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + draft + "/calendar", register(), null).assertStatus(404);
        call(HttpMethod.GET, path, organizer, null).assertStatus(200);
        call(HttpMethod.GET, path, admin(), null).assertStatus(200);
        call(HttpMethod.GET, "/api/events/999999999/calendar.ics", null, null).assertStatus(404);
    }

    @Test
    void cancellationReplacesTheEntryForTicketHolders() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        String email = emailOf(attendee);
        createOrder(attendee, event, type, 1);
        deliverQueuedEmails();

        // The confirmation carries the tickets and the calendar entry.
        OutgoingMail confirmation = mailWithSubject(email, "Your tickets for Test event");
        assertThat(confirmation.attachments()).extracting(OutgoingMail.Attachment::contentType)
                .containsExactly("application/pdf", "text/calendar; charset=utf-8");
        assertThat(calendarAttachment(confirmation).filename()).isEqualTo("biletflow-event-" + event + ".ics");
        assertThat(calendarText(confirmation)).contains("UID:event-" + event + "@localhost", "SEQUENCE:0",
                "STATUS:CONFIRMED");

        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);

        // The event page is gone for the public, but its ticket holder still gets the cancellation file.
        call(HttpMethod.GET, "/api/events/" + event, null, null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + event + "/calendar.ics", null, null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + event + "/calendar.ics", register(), null).assertStatus(404);
        assertThat(ics(attendee, event)).contains("UID:event-" + event + "@localhost", "SEQUENCE:1",
                "STATUS:CANCELLED", "SUMMARY:Cancelled: Test event",
                "DESCRIPTION:This event has been cancelled by the organizer.");

        JsonNode links = call(HttpMethod.GET, "/api/events/" + event + "/calendar", attendee, null).assertStatus(200).json();
        assertThat(links.get("cancelled").asBoolean()).isTrue();
        assertThat(links.get("icsUrl").asString()).endsWith("/calendar.ics");
        assertThat(links.get("googleCalendarUrl").isNull()).isTrue();
        assertThat(links.get("outlookUrl").isNull()).isTrue();

        deliverQueuedEmails();
        OutgoingMail cancellation = mailWithSubject(email, "Test event has been cancelled");
        assertThat(cancellation.text()).contains("calendar file (.ics)");
        assertThat(calendarText(cancellation)).contains("UID:event-" + event + "@localhost", "SEQUENCE:1",
                "STATUS:CANCELLED");
    }

    @Test
    void changedTimeSendsAnUpdatedCalendarFile() {
        String organizer = organizer();
        String title = "Moved " + UUID.randomUUID().toString().substring(0, 8);
        long event = publishedEvent(organizer, title, "Hall");
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);

        LocalDateTime newStart = LocalDateTime.of(LocalDateTime.now().getYear() + 1, 7, 1, 20, 0);
        call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("startTime", newStart.toString()))
                .assertStatus(200);
        deliverQueuedEmails();

        OutgoingMail update = mailWithSubject(emailOf(attendee), "Details changed for " + title);
        assertThat(update.attachments()).hasSize(1);
        assertThat(calendarText(update)).contains("UID:event-" + event + "@localhost", "SEQUENCE:1",
                "DTSTART;TZID=Asia/Almaty:" + compact(newStart) + "T200000");
    }

    private String ics(String token, long event) {
        return unfold(call(HttpMethod.GET, "/api/events/" + event + "/calendar.ics", token, null).assertStatus(200).text());
    }

    // RFC 5545 unfolding: a CRLF followed by a space continues the previous line.
    private static String unfold(String ics) {
        return ics.replace("\r\n ", "");
    }

    private static String compact(LocalDateTime time) {
        return time.toLocalDate().toString().replace("-", "");
    }

    private static String field(String ics, String prefix) {
        return ics.lines().filter(line -> line.startsWith(prefix)).findFirst().orElseThrow().substring(prefix.length());
    }

    private OutgoingMail mailWithSubject(String email, String subject) {
        return mailbox.sentTo(email).stream().filter(m -> m.subject().equals(subject)).findFirst().orElseThrow();
    }

    private static OutgoingMail.Attachment calendarAttachment(OutgoingMail mail) {
        return mail.attachments().stream().filter(a -> a.filename().endsWith(".ics")).findFirst().orElseThrow();
    }

    private static String calendarText(OutgoingMail mail) {
        return unfold(new String(calendarAttachment(mail).content(), StandardCharsets.UTF_8));
    }
}
