package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportCaseIntegrationTest extends ApiIntegrationTest {
    private static final String CASES = "/api/support/cases";

    @Test
    void attendeeAndOrganizerTalkInACaseThatKnowsItsContext() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        long orderId = createOrder(attendee, event, type, 1).get("id").asLong();
        long ticketId = tickets(attendee).get(0).get("id").asLong();

        JsonNode opened = open(attendee, Map.of("category", "TICKET_DELIVERY", "subject", "No email",
                "message", "My ticket never arrived", "ticketId", ticketId));

        JsonNode supportCase = opened.get("supportCase");
        assertThat(supportCase.get("type").asString()).isEqualTo("ATTENDEE");
        assertThat(supportCase.get("status").asString()).isEqualTo("OPEN");
        assertThat(supportCase.get("eventId").asLong()).isEqualTo(event);
        assertThat(supportCase.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(supportCase.get("ticketId").asLong()).isEqualTo(ticketId);
        assertThat(notificationTypes(organizer)).contains("SUPPORT_MESSAGE");

        long id = supportCase.get("id").asLong();
        reply(organizer, id, "Sent it again").assertStatus(200);
        JsonNode thread = call(HttpMethod.GET, CASES + "/" + id, attendee, null).assertStatus(200).json();
        assertThat(thread.get("messages")).hasSize(2);
        assertThat(thread.get("messages").get(1).get("body").asString()).isEqualTo("Sent it again");
        assertThat(thread.get("messages").get(1).get("fromStaff").asBoolean()).isTrue();
        assertThat(thread.get("messages").get(1).get("authorName").asString()).isEqualTo("Event organizer team");
        assertThat(notificationTypes(attendee)).contains("SUPPORT_MESSAGE");
    }

    @Test
    void casesAreVisibleOnlyToThoseRelatedToThem() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long otherEvent = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        long id = open(attendee, Map.of("category", "CHECK_IN", "subject", "Help", "message", "Hi", "eventId", event))
                .get("supportCase").get("id").asLong();

        String ownStaffEmail = "own-" + System.nanoTime() + "@test.kz";
        String ownStaff = staffOf(organizer, event, ownStaffEmail);
        String otherStaff = staffOf(organizer, otherEvent, "other-" + System.nanoTime() + "@test.kz");

        call(HttpMethod.GET, CASES + "/" + id, ownStaff, null).assertStatus(200);
        call(HttpMethod.GET, CASES + "/" + id, admin(), null).assertStatus(200);
        call(HttpMethod.GET, CASES + "/" + id, otherStaff, null).assertStatus(404);
        call(HttpMethod.GET, CASES + "/" + id, organizer(), null).assertStatus(404);
        call(HttpMethod.GET, CASES + "/" + id, register(), null).assertStatus(404);
        call(HttpMethod.GET, CASES + "/" + id, null, null).assertStatus(401);
        reply(otherStaff, id, "Peeking").assertStatus(404);

        assertThat(caseIds(attendee)).containsExactly(id);
        assertThat(caseIds(organizer)).containsExactly(id);
        assertThat(caseIds(ownStaff)).containsExactly(id);
        assertThat(caseIds(otherStaff)).isEmpty();
        assertThat(caseIds(register())).isEmpty();
    }

    @Test
    void aCaseCanOnlyReferToTheRequestersOwnOrderOrTicket() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long otherEvent = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String owner = register();
        long orderId = createOrder(owner, event, type, 1).get("id").asLong();
        long ticketId = tickets(owner).get(0).get("id").asLong();
        String stranger = register();
        Map<String, Object> base = Map.of("category", "PAYMENT", "subject", "S", "message", "M");

        call(HttpMethod.POST, CASES, stranger, with(base, "orderId", orderId)).assertStatus(404);
        call(HttpMethod.POST, CASES, stranger, with(base, "ticketId", ticketId)).assertStatus(404);
        call(HttpMethod.POST, CASES, stranger, base).assertStatus(400);
        Map<String, Object> mismatch = with(base, "orderId", orderId);
        mismatch.put("eventId", otherEvent);
        call(HttpMethod.POST, CASES, owner, mismatch).assertStatus(400);

        long draft = draftEvent(organizer, "Hidden", "Hall");
        call(HttpMethod.POST, CASES, stranger, with(base, "eventId", draft)).assertStatus(404);
    }

    @Test
    void staffManageACaseAndTheRequesterCannot() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String attendee = register();
        long id = open(attendee, Map.of("category", "REFUND", "subject", "Refund?", "message", "Please", "eventId", event))
                .get("supportCase").get("id").asLong();
        String staffEmail = "assignee-" + System.nanoTime() + "@test.kz";
        String staff = staffOf(organizer, event, staffEmail);

        call(HttpMethod.POST, CASES + "/" + id + "/status", attendee, Map.of("status", "RESOLVED")).assertStatus(403);
        call(HttpMethod.POST, CASES + "/" + id + "/assign", attendee, Map.of("email", staffEmail)).assertStatus(403);
        call(HttpMethod.POST, CASES + "/" + id + "/assign", organizer, Map.of("email", emailOf(register()))).assertStatus(400);

        JsonNode assigned = call(HttpMethod.POST, CASES + "/" + id + "/assign", organizer, Map.of("email", staffEmail))
                .assertStatus(200).json().get("supportCase");
        assertThat(assigned.get("assigneeEmail").asString()).isEqualTo(staffEmail);
        assertThat(notificationTypes(staff)).containsExactly("SUPPORT_ASSIGNED");

        // The requester doesn't get to see who the staff member is.
        assertThat(call(HttpMethod.GET, CASES + "/" + id, attendee, null).json().get("supportCase").get("assigneeEmail").isNull()).isTrue();

        reply(attendee, id, "Any news?").assertStatus(200);
        assertThat(notificationTypes(staff)).containsExactly("SUPPORT_MESSAGE", "SUPPORT_ASSIGNED");

        for (String status : List.of("IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED")) {
            assertThat(call(HttpMethod.POST, CASES + "/" + id + "/status", staff, Map.of("status", status))
                    .assertStatus(200).json().get("supportCase").get("status").asString()).isEqualTo(status);
        }
        assertThat(notificationTypes(attendee)).contains("SUPPORT_STATUS");
        assertThat(auditActions(event)).contains("SUPPORT_CASE_ASSIGNED", "SUPPORT_CASE_STATUS_CHANGED");
    }

    @Test
    void aResolvedCaseReopensWhenTheRequesterWritesAgainAndKeepsItsHistory() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String attendee = register();
        long id = open(attendee, Map.of("category", "OTHER", "subject", "Q", "message", "First", "eventId", event))
                .get("supportCase").get("id").asLong();
        call(HttpMethod.POST, CASES + "/" + id + "/status", organizer, Map.of("status", "RESOLVED")).assertStatus(200);

        reply(organizer, id, "One more thing").assertStatus(400);
        JsonNode reopened = reply(attendee, id, "It happened again").assertStatus(200).json();

        assertThat(reopened.get("supportCase").get("status").asString()).isEqualTo("OPEN");
        assertThat(reopened.get("supportCase").get("resolvedAt").isNull()).isTrue();
        assertThat(reopened.get("messages")).hasSize(2);
    }

    @Test
    void platformCasesAreBetweenTheOrganizerAndPlatformAdmins() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String admin = admin();
        Map<String, Object> body = Map.of("type", "PLATFORM", "category", "ACTIVATION", "subject", "Stuck", "message", "Checklist is stuck");

        call(HttpMethod.POST, CASES, register(), body).assertStatus(403);
        call(HttpMethod.POST, CASES, organizer, with(body, "eventId", publishedEvent(organizer()))).assertStatus(404);

        long id = open(organizer, with(body, "eventId", event)).get("supportCase").get("id").asLong();
        assertThat(notificationTypes(admin)).contains("SUPPORT_MESSAGE");

        String staff = staffOf(organizer, event, "platform-" + System.nanoTime() + "@test.kz");
        call(HttpMethod.GET, CASES + "/" + id, staff, null).assertStatus(404);
        call(HttpMethod.GET, CASES + "/" + id, organizer(), null).assertStatus(404);

        JsonNode answered = reply(admin, id, "Looking into it").assertStatus(200).json();
        assertThat(answered.get("messages").get(1).get("authorName").asString()).isEqualTo("BiletFlow Support");
        // The organizer is the customer here, so can't close their own platform case.
        call(HttpMethod.POST, CASES + "/" + id + "/status", organizer, Map.of("status", "RESOLVED")).assertStatus(403);
    }

    @Test
    void organizerCanEscalateACaseForPlatformAdmins() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        String attendee = register();
        long id = open(attendee, Map.of("category", "PAYMENT", "subject", "Dispute", "message", "Charged twice", "eventId", event))
                .get("supportCase").get("id").asLong();
        String admin = admin();

        call(HttpMethod.POST, CASES + "/" + id + "/escalate", attendee, null).assertStatus(403);
        call(HttpMethod.POST, CASES + "/" + id + "/escalate", organizer, null).assertStatus(200);
        call(HttpMethod.POST, CASES + "/" + id + "/escalate", organizer, null).assertStatus(400);

        assertThat(list(call(HttpMethod.GET, CASES + "?escalated=true&size=100", admin, null).assertStatus(200).json().get("content")))
                .anySatisfy(c -> assertThat(c.get("id").asLong()).isEqualTo(id));
    }

    // helpers //

    private JsonNode open(String user, Map<String, Object> body) {
        return call(HttpMethod.POST, CASES, user, body).assertStatus(201).json();
    }

    private Response reply(String user, long caseId, String text) {
        return call(HttpMethod.POST, CASES + "/" + caseId + "/messages", user, Map.of("body", text));
    }

    private List<Long> caseIds(String user) {
        return list(call(HttpMethod.GET, CASES + "?size=100", user, null).assertStatus(200).json().get("content")).stream()
                .map(c -> c.get("id").asLong())
                .toList();
    }

    private String staffOf(String organizer, long event, String email) {
        register(email);
        jdbcTemplate.update("UPDATE users SET role = 'EVENT_ADMIN' WHERE email = ?", email);
        call(HttpMethod.POST, "/api/events/" + event + "/staff", organizer, Map.of("email", email)).assertStatus(201);
        return login(email, PASSWORD);
    }

    private Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(base);
        copy.put(key, value);
        return copy;
    }
}
