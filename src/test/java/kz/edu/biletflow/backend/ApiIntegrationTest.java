package kz.edu.biletflow.backend;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import kz.edu.biletflow.backend.configs.NotificationEmailDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Helpers for tests that drive the API over real HTTP. Every test creates its own users and events.
@Import(CapturingMailGateway.Config.class)
abstract class ApiIntegrationTest extends IntegrationTestBase {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String PASSWORD = "Password123!";

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationEmailDispatcher notificationEmailDispatcher;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    protected String register() {
        return register("user-" + UUID.randomUUID() + "@test.kz");
    }

    protected String register(String email) {
        return call(HttpMethod.POST, "/api/auth/register", null, Map.of("email", email, "password", PASSWORD))
                .assertStatus(201).json().get("accessToken").asString();
    }

    protected String login(String email, String password) {
        return call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", password))
                .assertStatus(200).json().get("accessToken").asString();
    }

    protected String admin() {
        return withRole("PLATFORM_ADMIN");
    }

    protected String eventAdmin() {
        return withRole("EVENT_ADMIN");
    }

    private String withRole(String role) {
        String email = "staff-" + UUID.randomUUID() + "@test.kz";
        register(email);
        jdbcTemplate.update("UPDATE users SET role = ? WHERE email = ?", role, email);
        return login(email, PASSWORD);
    }

    protected String emailOf(String token) {
        return call(HttpMethod.GET, "/api/users/me", token, null).assertStatus(200).json().get("email").asString();
    }

    protected String organizer() {
        return call(HttpMethod.POST, "/api/organizers/profile", register(), Map.of("contactInformation", "+7 700 000 00 00"))
                .assertStatus(201).json().get("accessToken").asString();
    }

    protected long publishedEvent(String organizer) {
        return publishedEvent(organizer, "Test event", "Test Hall");
    }

    protected long publishedEvent(String organizer, String title, String venueName) {
        long event = draftEvent(organizer, title, venueName);
        call(HttpMethod.POST, "/api/events/" + event + "/publish", organizer, null).assertStatus(200);
        return event;
    }

    protected long draftEvent(String organizer, String title, String venueName) {
        long venue = call(HttpMethod.POST, "/api/venues", organizer,
                Map.of("name", venueName, "address", "Test street 1, Astana", "capacity", 500))
                .assertStatus(201).json().get("id").asLong();
        return call(HttpMethod.POST, "/api/events", organizer, Map.of(
                "title", title,
                "category", "Test",
                "startTime", LocalDateTime.now().plusDays(30).toString(),
                "visibilityStatus", "PUBLIC",
                "venueId", venue))
                .assertStatus(201).json().get("id").asLong();
    }

    // Tests about something other than activation get a paid event that can already sell.
    protected long ticketType(String organizer, long event, int price, int quantity) {
        long type = ticketTypeWithoutActivation(organizer, event, price, quantity);
        if (price > 0) {
            jdbcTemplate.update("UPDATE events SET paid_sales_active = true WHERE id = ?", event);
        }
        return type;
    }

    protected long ticketTypeWithoutActivation(String organizer, long event, int price, int quantity) {
        return call(HttpMethod.POST, "/api/events/" + event + "/ticket-types", organizer,
                Map.of("name", "Standard", "price", price, "quantity", quantity, "maxPerOrder", 10))
                .assertStatus(201).json().get("id").asLong();
    }

    protected Map<String, Object> orderBody(long event, long type, int quantity) {
        return Map.of("eventId", event, "items", List.of(Map.of("ticketTypeId", type, "quantity", quantity)));
    }

    protected JsonNode createOrder(String buyer, long event, long type, int quantity) {
        return call(HttpMethod.POST, "/api/orders", buyer, orderBody(event, type, quantity)).assertStatus(201).json();
    }

    protected Response pay(String buyer, long orderId) {
        return call(HttpMethod.POST, "/api/orders/" + orderId + "/pay", buyer, null);
    }

    protected List<JsonNode> tickets(String owner) {
        return list(call(HttpMethod.GET, "/api/tickets?size=100", owner, null).assertStatus(200).json().get("content"));
    }

    protected List<JsonNode> payments(String buyer, long orderId) {
        return list(call(HttpMethod.GET, "/api/orders/" + orderId + "/payments", buyer, null).assertStatus(200).json());
    }

    protected List<JsonNode> notifications(String user) {
        return list(call(HttpMethod.GET, "/api/notifications?size=100", user, null).assertStatus(200).json().get("content"));
    }

    protected List<String> notificationTypes(String user) {
        return notifications(user).stream().map(n -> n.get("type").asString()).toList();
    }

    protected List<String> auditActions(long event) {
        return jdbcTemplate.queryForList("SELECT action FROM audit_log WHERE event_id = ? ORDER BY id", String.class, event);
    }

    protected JsonNode scan(String staff, long event, String code) {
        return call(HttpMethod.POST, "/api/check-in/events/" + event + "/scan", staff, Map.of("code", code))
                .assertStatus(200).json();
    }

    protected void deliverQueuedEmails() {
        while (notificationEmailDispatcher.dispatchDue() > 0) {
            // keep going until nothing is due
        }
    }

    protected static List<JsonNode> list(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    protected Response call(HttpMethod method, String path, String token, Object body) {
        RestTestClient.RequestBodySpec request = client.method(method).uri(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        EntityExchangeResult<byte[]> result = request.exchange().expectBody().returnResult();
        return new Response(method + " " + path, result.getStatus().value(),
                result.getResponseHeaders().getContentType(), result.getResponseBody());
    }

    protected String decodeQr(byte[] png) throws Exception {
        return decodeQr(ImageIO.read(new ByteArrayInputStream(png)));
    }

    protected String decodeQr(BufferedImage image) throws Exception {
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        // PURE_BARCODE because this is a clean rendered image: ZXing's camera-oriented detector
        // misses about 2% of perfectly valid codes when given one.
        return new MultiFormatReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
    }

    // Runs the task on n threads released at the same moment, and returns every result.
    protected <T> List<T> concurrently(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    protected record Response(String request, int status, MediaType contentType, byte[] body) {
        Response assertStatus(int expected) {
            assertThat(status).as("%s responded with: %s", request, text()).isEqualTo(expected);
            return this;
        }

        JsonNode json() {
            return JSON.readTree(body);
        }

        String text() {
            return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
