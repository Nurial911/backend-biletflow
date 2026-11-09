package kz.edu.biletflow.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Answers like Halyk's test environment does: the response shapes were captured from real test payments.
final class FakeHalykServer {
    static final String TERMINAL = "test-terminal";

    private final HttpServer server;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private final Map<String, String> amounts = new ConcurrentHashMap<>();
    private final List<String> operations = new CopyOnWriteArrayList<>();
    volatile boolean down;
    volatile boolean refusesCharges;

    private static final class Transaction {
        final String id = UUID.randomUUID().toString();
        final BigDecimal amount;
        volatile String status;

        Transaction(BigDecimal amount, String status) {
            this.amount = amount;
            this.status = status;
        }
    }

    private FakeHalykServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/oauth2/token", this::token);
        server.createContext("/check-status/payment/transaction/", this::status);
        server.createContext("/operation/", this::operation);
        server.start();
    }

    static FakeHalykServer start() {
        try {
            return new FakeHalykServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    void authorize(String invoiceId, int amount) {
        transactions.put(invoiceId, new Transaction(BigDecimal.valueOf(amount), "AUTH"));
    }

    void charged(String invoiceId, int amount) {
        transactions.put(invoiceId, new Transaction(BigDecimal.valueOf(amount), "CHARGE"));
    }

    void decline(String invoiceId, int amount) {
        transactions.put(invoiceId, new Transaction(BigDecimal.valueOf(amount), "REJECT"));
    }

    String statusOf(String invoiceId) {
        return transactions.get(invoiceId).status;
    }

    String secretOf(String invoiceId) {
        return secrets.get(invoiceId);
    }

    String tokenAmountOf(String invoiceId) {
        return amounts.get(invoiceId);
    }

    long operationCount(String name, String invoiceId) {
        String id = transactions.get(invoiceId).id;
        return operations.stream().filter((name + ":" + id)::equals).count();
    }

    private void token(HttpExchange exchange) throws IOException {
        if (refuseWhenDown(exchange)) {
            return;
        }
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String invoiceId = field(form, "invoiceID");
        if (invoiceId != null) {
            secrets.put(invoiceId, field(form, "secret_hash"));
            amounts.put(invoiceId, field(form, "amount"));
        }
        respond(exchange, 200, "{\"access_token\":\"fake-token\",\"expires_in\":\"1200\",\"refresh_token\":\"\",\"scope\":\"payment\",\"token_type\":\"Bearer\"}");
    }

    private void status(HttpExchange exchange) throws IOException {
        if (refuseWhenDown(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String invoiceId = path.substring(path.lastIndexOf('/') + 1);
        Transaction t = transactions.get(invoiceId);
        if (t == null) {
            respond(exchange, 200, "{\"resultCode\":\"102\",\"resultMessage\":\"invoice not found\",\"transaction\":{\"id\":\"\",\"invoiceID\":\"" + invoiceId + "\",\"amount\":0}}");
            return;
        }
        String resultCode = "REJECT".equals(t.status) ? "101" : "100";
        respond(exchange, 200, "{\"resultCode\":\"" + resultCode + "\",\"resultMessage\":\"" + t.status + "\",\"transaction\":{"
                + "\"id\":\"" + t.id + "\",\"invoiceID\":\"" + invoiceId + "\",\"amount\":" + t.amount + ",\"currency\":\"KZT\","
                + "\"terminalID\":\"" + TERMINAL + "\",\"reason\":\"Test reason\",\"statusName\":\"" + t.status + "\"}}");
    }

    private void operation(HttpExchange exchange) throws IOException {
        if (refuseWhenDown(exchange)) {
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        String id = parts[2];
        String name = parts[3];
        if ("charge".equals(name) && refusesCharges) {
            respond(exchange, 400, "{\"code\":100,\"message\":\"refused\"}");
            return;
        }
        operations.add(name + ":" + id);
        transactions.values().stream().filter(t -> t.id.equals(id)).findFirst().ifPresent(t -> t.status = switch (name) {
            case "charge" -> "CHARGE";
            case "cancel" -> "CANCEL";
            case "refund" -> "REFUND";
            default -> t.status;
        });
        respond(exchange, 200, "{\"code\":0,\"message\":\"success\"}");
    }

    private boolean refuseWhenDown(HttpExchange exchange) throws IOException {
        if (down) {
            respond(exchange, 503, "unavailable");
        }
        return down;
    }

    private static String field(String multipart, String name) {
        Matcher matcher = Pattern.compile("name=\"" + name + "\"(?:\\r\\n[^\\r\\n]+)*\\r\\n\\r\\n([^\\r\\n]*)").matcher(multipart);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
