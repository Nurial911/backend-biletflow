package kz.edu.biletflow.backend;

import kz.edu.biletflow.backend.services.OutgoingMail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Admin user search and user suspension (SRS 4.12).
class UserAdministrationIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private CapturingMailGateway mailbox;

    @Test
    void adminSearchesUsersByEmailNameIdRoleAndSuspension() {
        String admin = admin();
        String marker = "find" + UUID.randomUUID().toString().substring(0, 8);
        String alice = register(marker + "-alice@test.kz");
        String bob = register(marker + "-bob@test.kz");
        long bobId = userId(bob);
        String surname = "Nm" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("UPDATE users SET full_name = ? WHERE id = ?", "Bob " + surname, bobId);
        String organizer = organizer();
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", marker + "-org@test.kz", userId(organizer));

        assertThat(emails(search(admin, "?q=" + marker)))
                .containsExactlyInAnyOrder(marker + "-alice@test.kz", marker + "-bob@test.kz", marker + "-org@test.kz");
        assertThat(emails(search(admin, "?q=" + marker.toUpperCase() + "-ALICE"))).containsExactly(marker + "-alice@test.kz");
        assertThat(emails(search(admin, "?q=" + surname.toLowerCase()))).containsExactly(marker + "-bob@test.kz"); // by name
        assertThat(emails(search(admin, "?q=" + bobId))).contains(marker + "-bob@test.kz");
        assertThat(emails(search(admin, "?q=" + marker + "&role=ORGANIZER"))).containsExactly(marker + "-org@test.kz");
        // _ is matched literally: as a LIKE wildcard it would match the "-" after the marker.
        assertThat(search(admin, "?q=" + marker + "_").get("totalElements").asLong()).isZero();

        call(HttpMethod.POST, "/api/users/" + userId(alice) + "/suspend", admin, null).assertStatus(200);
        assertThat(emails(search(admin, "?q=" + marker + "&suspended=true"))).containsExactly(marker + "-alice@test.kz");
        assertThat(emails(search(admin, "?q=" + marker + "&suspended=false"))).hasSize(2);

        call(HttpMethod.GET, "/api/users?q=" + marker, bob, null).assertStatus(403);
        call(HttpMethod.GET, "/api/users?role=NOBODY", admin, null).assertStatus(400);
    }

    @Test
    void suspendedUserCannotSignInOrUseTokensAlreadyIssued() {
        String admin = admin();
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        String email = "suspend-" + UUID.randomUUID() + "@test.kz";
        String user = register(email);
        long paidOrder = createOrder(user, event, type, 1).get("id").asLong();
        pay(user, paidOrder).assertStatus(200);
        String ticketCode = tickets(user).get(0).get("code").asString();
        long pendingOrder = createOrder(user, event, type, 2).get("id").asLong();
        long id = userId(user);

        JsonNode suspended = call(HttpMethod.POST, "/api/users/" + id + "/suspend", admin,
                Map.of("reason", "  Reselling tickets  ")).assertStatus(200).json();
        assertThat(suspended.get("suspended").asBoolean()).isTrue();
        assertThat(suspended.get("suspensionReason").asString()).isEqualTo("Reselling tickets");
        assertThat(suspended.get("suspendedAt").isNull()).isFalse();

        // The old token is refused everywhere, public routes included.
        Response me = call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(403);
        assertThat(me.json().get("message").asString()).isEqualTo("This account has been suspended");
        assertThat(me.code()).isEqualTo("ACCOUNT_SUSPENDED");
        call(HttpMethod.GET, "/api/events", user, null).assertStatus(403);
        call(HttpMethod.GET, "/api/events", null, null).assertStatus(200);

        Response login = call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .assertStatus(403);
        assertThat(login.json().get("message").asString())
                .isEqualTo("This account has been suspended: Reselling tickets");
        assertThat(login.code()).isEqualTo("ACCOUNT_SUSPENDED");
        // A wrong password doesn't reveal that the account is suspended.
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", "Wrong123!"))
                .assertStatus(401);

        // Reservations are released; a bought ticket still admits its holder.
        assertThat(orderStatus(pendingOrder)).isEqualTo("CANCELLED");
        assertThat(orderStatus(paidOrder)).isEqualTo("PAID");
        assertThat(scan(organizer, event, ticketCode).get("result").asString()).isEqualTo("CHECKED_IN");

        deliverQueuedEmails();
        OutgoingMail mail = mailbox.sentTo(email).stream()
                .filter(m -> m.subject().equals("Your BiletFlow account has been suspended"))
                .findFirst().orElseThrow();
        assertThat(mail.text()).contains("Reselling tickets");
        assertThat(userAuditActions(id)).containsExactly("USER_SUSPENDED");

        JsonNode restored = call(HttpMethod.POST, "/api/users/" + id + "/unsuspend", admin, null).assertStatus(200).json();
        assertThat(restored.get("suspended").asBoolean()).isFalse();
        assertThat(restored.get("suspensionReason").isNull()).isTrue();
        call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(200); // the old token works again
        login(email, PASSWORD);
        assertThat(userAuditActions(id)).containsExactly("USER_SUSPENDED", "USER_UNSUSPENDED");
        assertThat(notificationTypes(user)).contains("ACCOUNT_SUSPENDED", "ACCOUNT_REINSTATED");
    }

    @Test
    void suspensionRulesAndAccess() {
        String admin = admin();
        String user = register();
        long id = userId(user);
        String path = "/api/users/" + id;

        call(HttpMethod.POST, "/api/users/" + userId(admin) + "/suspend", admin, null).assertStatus(400); // yourself
        call(HttpMethod.POST, "/api/users/" + userId(admin()) + "/suspend", admin, null).assertStatus(400); // an admin
        call(HttpMethod.POST, "/api/users/999999999/suspend", admin, null).assertStatus(404);
        call(HttpMethod.POST, path + "/suspend", admin, Map.of("reason", "x".repeat(501))).assertStatus(400);
        call(HttpMethod.POST, path + "/unsuspend", admin, null).assertStatus(400); // not suspended
        call(HttpMethod.POST, path + "/suspend", register(), null).assertStatus(403);
        call(HttpMethod.POST, path + "/suspend", null, null).assertStatus(401);

        JsonNode suspended = call(HttpMethod.POST, path + "/suspend", admin, null).assertStatus(200).json();
        assertThat(suspended.get("suspensionReason").isNull()).isTrue();
        call(HttpMethod.POST, path + "/suspend", admin, null).assertStatus(400); // already suspended
        assertThat(call(HttpMethod.GET, path, admin, null).assertStatus(200).json().get("suspended").asBoolean()).isTrue();
    }

    @Test
    void tokenOfADeletedAccountNoLongerAuthenticates() {
        String user = register();
        call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(200);
        call(HttpMethod.DELETE, "/api/users/me", user, null).assertStatus(204);
        call(HttpMethod.GET, "/api/users/me", user, null).assertStatus(401);
        call(HttpMethod.GET, "/api/events", user, null).assertStatus(200); // public routes treat it as anonymous
    }

    private long userId(String token) {
        return call(HttpMethod.GET, "/api/users/me", token, null).assertStatus(200).json().get("id").asLong();
    }

    private JsonNode search(String admin, String query) {
        return call(HttpMethod.GET, "/api/users" + query, admin, null).assertStatus(200).json();
    }

    private static List<String> emails(JsonNode page) {
        return list(page.get("content")).stream().map(u -> u.get("email").asString()).toList();
    }

    private String orderStatus(long order) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order);
    }

    private List<String> userAuditActions(long userId) {
        return jdbcTemplate.queryForList(
                "SELECT action FROM audit_log WHERE entity_id = ? AND action LIKE 'USER_%' ORDER BY id", String.class, userId);
    }
}
