package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

// /actuator/health is what deploy/deploy.sh polls after each deployment.
class HealthEndpointIntegrationTest extends ApiIntegrationTest {

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() {
        JsonNode health = call(HttpMethod.GET, "/actuator/health", null, null).assertStatus(200).json();
        // UP although no mail server runs in tests: the mail check is off while MAIL_ENABLED=false.
        assertThat(health.get("status").asString()).isEqualTo("UP");
        assertThat(health.has("components")).isFalse();
        assertThat(health.has("details")).isFalse();
    }

    @Test
    void noOtherActuatorEndpointIsExposed() {
        call(HttpMethod.GET, "/actuator/env", null, null).assertStatus(401);
        call(HttpMethod.GET, "/actuator/env", admin(), null).assertStatus(404);
        call(HttpMethod.GET, "/actuator/beans", admin(), null).assertStatus(404);
    }

    @Test
    void unknownUrlsAndMethodsAreNotServerErrors() {
        String user = register();
        call(HttpMethod.GET, "/api/no-such-thing", user, null).assertStatus(404);
        call(HttpMethod.DELETE, "/actuator/health", user, null).assertStatus(405);
    }
}
