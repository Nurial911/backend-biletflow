package kz.edu.biletflow.backend;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import kz.edu.biletflow.backend.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ticketing path of the demo, driven over real HTTP: order, payment, ticket, QR image, scan.
 * Every test creates its own users and event, so tests don't depend on each other.
 */
class CoreFlowIntegrationTest extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // orders //

    @Test
    void freeOrderIsPaidImmediatelyAndIssuesOneTicketPerUnit() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();

        JsonNode order = createOrder(attendee, event, type, 2);

        assertThat(order.get("status").asString()).isEqualTo("PAID");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("0");
        List<JsonNode> tickets = tickets(attendee);
        assertThat(tickets).hasSize(2);
        assertThat(tickets).allSatisfy(t -> assertThat(t.get("status").asString()).isEqualTo("VALID"));
        assertThat(tickets.get(0).get("code").asString()).isNotEqualTo(tickets.get(1).get("code").asString());
    }

    @Test
    void paidOrderIssuesTicketsOnlyAfterPayment() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 2500, 10);
        String attendee = register();

        JsonNode order = createOrder(attendee, event, type, 3);

        assertThat(order.get("status").asString()).isEqualTo("PENDING");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("7500");
        assertThat(tickets(attendee)).isEmpty();

        JsonNode paid = pay(attendee, order.get("id").asLong()).assertStatus(200).json();

        assertThat(paid.get("status").asString()).isEqualTo("PAID");
        assertThat(tickets(attendee)).hasSize(3);
    }

    @Test
    void concurrentPaymentsIssueTicketsOnlyOnce() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 30);
        String attendee = register();

        int orders = 3;
        for (int i = 0; i < orders; i++) {
            long orderId = createOrder(attendee, event, type, 2).get("id").asLong();

            List<Integer> statuses = concurrently(8, () -> pay(attendee, orderId).status());

            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 400).hasSize(7);
        }
        assertThat(tickets(attendee)).hasSize(orders * 2);
    }

    @Test
    void concurrentOrdersCannotOversellInventory() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 5);
        String attendee = register();

        List<Integer> statuses = concurrently(12,
                () -> call(HttpMethod.POST, "/api/orders", attendee, orderBody(event, type, 1)).status());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(7);
    }

    @Test
    void cancellingAPendingOrderReleasesInventoryAndBlocksPayment() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 1);
        String attendee = register();
        long orderId = createOrder(attendee, event, type, 1).get("id").asLong();

        call(HttpMethod.POST, "/api/orders", register(), orderBody(event, type, 1)).assertStatus(400);
        call(HttpMethod.POST, "/api/orders/" + orderId + "/cancel", attendee, null).assertStatus(200);

        pay(attendee, orderId).assertStatus(400);
        assertThat(tickets(attendee)).isEmpty();
        createOrder(register(), event, type, 1);
    }

    @Test
    void expiredOrderCannotBePaidAndReleasesInventory() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 1);
        String attendee = register();
        long orderId = createOrder(attendee, event, type, 1).get("id").asLong();

        jdbcTemplate.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), orderId);

        pay(attendee, orderId).assertStatus(400);
        orderService.expireStaleOrders();
        assertThat(call(HttpMethod.GET, "/api/orders/" + orderId, attendee, null).json().get("status").asString())
                .isEqualTo("EXPIRED");
        createOrder(register(), event, type, 1);
    }

    @Test
    void anotherUsersOrderLooksMissing() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);
        long orderId = createOrder(register(), event, type, 1).get("id").asLong();
        String stranger = register();

        call(HttpMethod.GET, "/api/orders/" + orderId, stranger, null).assertStatus(404);
        pay(stranger, orderId).assertStatus(404);
        call(HttpMethod.POST, "/api/orders/" + orderId + "/cancel", stranger, null).assertStatus(404);
    }

    @Test
    void orderingRequiresAToken() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 1000, 10);

        call(HttpMethod.POST, "/api/orders", null, orderBody(event, type, 1)).assertStatus(401);
    }

    // tickets and check-in //

    @Test
    void qrImageEncodesTheCodeThatCheckInAccepts() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        JsonNode ticket = tickets(attendee).get(0);

        Response qr = call(HttpMethod.GET, "/api/tickets/" + ticket.get("id").asLong() + "/qr", attendee, null)
                .assertStatus(200);

        assertThat(qr.contentType()).isEqualTo(MediaType.IMAGE_PNG);
        String decoded = decodeQr(qr.body());
        assertThat(decoded).isEqualTo(ticket.get("code").asString());
        assertThat(scan(organizer, event, decoded).get("result").asString()).isEqualTo("CHECKED_IN");
    }

    @Test
    void ticketAdmitsOnceUntilTheCheckInIsUndone() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        JsonNode ticket = tickets(attendee).get(0);
        String code = ticket.get("code").asString();

        assertThat(scan(organizer, event, code).get("result").asString()).isEqualTo("CHECKED_IN");
        assertThat(scan(organizer, event, code).get("result").asString()).isEqualTo("ALREADY_USED");

        call(HttpMethod.POST, "/api/check-in/events/" + event + "/tickets/" + ticket.get("id").asLong() + "/undo",
                organizer, null).assertStatus(200);

        assertThat(scan(organizer, event, code).get("result").asString()).isEqualTo("CHECKED_IN");
    }

    @Test
    void concurrentScansAdmitTheTicketOnce() throws Exception {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        String code = tickets(attendee).get(0).get("code").asString();

        List<String> results = concurrently(8, () -> scan(organizer, event, code).get("result").asString());

        assertThat(results).filteredOn("CHECKED_IN"::equals).hasSize(1);
        assertThat(results).filteredOn("ALREADY_USED"::equals).hasSize(7);
    }

    @Test
    void scanRejectsUnknownCodesAndOtherEventsTickets() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long otherEvent = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        String code = tickets(attendee).get(0).get("code").asString();

        assertThat(scan(organizer, event, UUID.randomUUID().toString()).get("result").asString()).isEqualTo("INVALID");
        assertThat(scan(organizer, otherEvent, code).get("result").asString()).isEqualTo("WRONG_EVENT");
    }

    @Test
    void cancellingAnEventInvalidatesItsTickets() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        long type = ticketType(organizer, event, 0, 10);
        String attendee = register();
        createOrder(attendee, event, type, 1);
        String code = tickets(attendee).get(0).get("code").asString();

        call(HttpMethod.POST, "/api/events/" + event + "/cancel", organizer, null).assertStatus(200);

        assertThat(scan(organizer, event, code).get("result").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void onlyAuthorizedStaffCanScan() {
        String organizer = organizer();
        long event = publishedEvent(organizer);
        Map<String, String> body = Map.of("code", UUID.randomUUID().toString());
        String path = "/api/check-in/events/" + event + "/scan";

        call(HttpMethod.POST, path, register(), body).assertStatus(403);
        call(HttpMethod.POST, path, organizer(), body).assertStatus(404);
    }

    // helpers //

    private String register() {
        Map<String, String> body = Map.of("email", "user-" + UUID.randomUUID() + "@test.kz", "password", "Password123!");
        return call(HttpMethod.POST, "/api/auth/register", null, body).assertStatus(201).json().get("accessToken").asString();
    }

    private String organizer() {
        return call(HttpMethod.POST, "/api/organizers/profile", register(), Map.of("contactInformation", "+7 700 000 00 00"))
                .assertStatus(201).json().get("accessToken").asString();
    }

    private long publishedEvent(String organizer) {
        long venue = call(HttpMethod.POST, "/api/venues", organizer,
                Map.of("name", "Test Hall", "address", "Test street 1, Astana", "capacity", 500))
                .assertStatus(201).json().get("id").asLong();
        long event = call(HttpMethod.POST, "/api/events", organizer, Map.of(
                "title", "Test event",
                "category", "Test",
                "startTime", LocalDateTime.now().plusDays(30).toString(),
                "visibilityStatus", "PUBLIC",
                "venueId", venue))
                .assertStatus(201).json().get("id").asLong();
        call(HttpMethod.POST, "/api/events/" + event + "/publish", organizer, null).assertStatus(200);
        return event;
    }

    private long ticketType(String organizer, long event, int price, int quantity) {
        return call(HttpMethod.POST, "/api/events/" + event + "/ticket-types", organizer,
                Map.of("name", "Standard", "price", price, "quantity", quantity, "maxPerOrder", 10))
                .assertStatus(201).json().get("id").asLong();
    }

    private Map<String, Object> orderBody(long event, long type, int quantity) {
        return Map.of("eventId", event, "items", List.of(Map.of("ticketTypeId", type, "quantity", quantity)));
    }

    private JsonNode createOrder(String buyer, long event, long type, int quantity) {
        return call(HttpMethod.POST, "/api/orders", buyer, orderBody(event, type, quantity)).assertStatus(201).json();
    }

    private Response pay(String buyer, long orderId) {
        return call(HttpMethod.POST, "/api/orders/" + orderId + "/pay", buyer, null);
    }

    private List<JsonNode> tickets(String owner) {
        List<JsonNode> tickets = new ArrayList<>();
        call(HttpMethod.GET, "/api/tickets?size=100", owner, null).assertStatus(200).json().get("content").forEach(tickets::add);
        return tickets;
    }

    private JsonNode scan(String staff, long event, String code) {
        return call(HttpMethod.POST, "/api/check-in/events/" + event + "/scan", staff, Map.of("code", code))
                .assertStatus(200).json();
    }

    private Response call(HttpMethod method, String path, String token, Object body) {
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

    private String decodeQr(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    // Runs the task on n threads released at the same moment, and returns every result.
    private <T> List<T> concurrently(int n, Callable<T> task) throws Exception {
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

    private record Response(String request, int status, MediaType contentType, byte[] body) {
        Response assertStatus(int expected) {
            assertThat(status).as("%s responded with: %s", request, text()).isEqualTo(expected);
            return this;
        }

        JsonNode json() {
            return JSON.readTree(body);
        }

        private String text() {
            return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
