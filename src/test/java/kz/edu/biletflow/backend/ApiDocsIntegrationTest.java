package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

// The frontend reads the sales contract from Swagger, so the documented rules must reach /v3/api-docs.
class ApiDocsIntegrationTest extends ApiIntegrationTest {

    @Test
    void salesFiltersAreDocumented() {
        JsonNode docs = call(HttpMethod.GET, "/v3/api-docs", null, null).assertStatus(200).json();
        JsonNode sales = docs.get("paths").get("/api/organizer/events/{eventId}/sales").get("get");

        assertThat(sales.get("description").asString()).contains("narrow only `salesByDay`", "Asia/Almaty");
        assertThat(sales.get("parameters").toString()).contains("inclusive", "from 00:00 Asia/Almaty");
        assertThat(docs.get("components").get("schemas").get("DailySalesResponse").get("description").asString())
                .contains("Days without sales are not listed");
    }
}
