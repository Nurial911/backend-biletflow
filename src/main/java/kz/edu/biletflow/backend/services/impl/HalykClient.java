package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.configs.HalykConfig;
import kz.edu.biletflow.backend.exception.PaymentProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Talks to Halyk ePay. Hosts and behaviour were confirmed against the live test environment, because
 * parts of Halyk's documentation are out of date: expired hosts, and a 2-hour token that really lasts 20 minutes.
 */
@Slf4j
@Component
public class HalykClient {
    private static final String OPERATIONS_SCOPE = "webapi usermanagement email_send verification statement statistics payment";
    private static final String RESULT_SUCCESS = "100";
    private static final String RESULT_REJECTED = "101";
    private static final String RESULT_NOT_FOUND = "102";

    private final HalykConfig config;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public HalykClient(HalykConfig config) {
        this.config = config;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** One attempt's state at the bank. statusName is AUTH (held), CHARGE (taken), REJECT, CANCEL or REFUND. */
    public record Transaction(String id, String invoiceId, BigDecimal amount, String currency,
                              String terminalId, String statusName, String reason) {
    }

    // Limited to this invoice, so it is safe to hand to the shopper's browser.
    public JsonNode createPaymentToken(String invoiceId, String secretHash, BigDecimal amount, String postLink) {
        MultiValueMap<String, Object> form = baseForm("payment");
        form.add("invoiceID", invoiceId);
        form.add("secret_hash", secretHash);
        form.add("amount", amount.toPlainString());
        form.add("currency", "KZT");
        form.add("postLink", postLink);
        form.add("failurePostLink", postLink);
        return requestToken(form);
    }

    // Empty until the shopper has submitted a card.
    public Optional<Transaction> findTransaction(String invoiceId) {
        String body = get(config.getApiUrl() + "/check-status/payment/transaction/" + invoiceId);
        JsonNode root = parse(body);
        String resultCode = root.path("resultCode").asString("");
        if (RESULT_NOT_FOUND.equals(resultCode)) {
            return Optional.empty();
        }
        if (!RESULT_SUCCESS.equals(resultCode) && !RESULT_REJECTED.equals(resultCode)) {
            throw new PaymentProviderException("The bank could not report the payment status (code " + resultCode + ")");
        }
        JsonNode t = root.path("transaction");
        String id = t.path("id").asString("");
        if (id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Transaction(id, t.path("invoiceID").asString(""),
                new BigDecimal(t.path("amount").asString("0")), t.path("currency").asString(""),
                t.path("terminalID").asString(""), t.path("statusName").asString(""), t.path("reason").asString("")));
    }

    // Safe to repeat: charging an already charged payment succeeds without taking more.
    public void charge(String transactionId) {
        operation(transactionId, "charge");
    }

    // Releases a hold that was never taken.
    public void cancel(String transactionId) {
        operation(transactionId, "cancel");
    }

    // Returns money that was taken.
    public void refund(String transactionId) {
        operation(transactionId, "refund");
    }

    private void operation(String transactionId, String name) {
        String url = config.getApiUrl() + "/operation/" + transactionId + "/" + name;
        try {
            restClient.post().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationsToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentProviderException("The bank refused the " + name + " request", e);
        }
    }

    private String get(String url) {
        try {
            return restClient.get().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationsToken())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new PaymentProviderException("The bank could not be reached", e);
        }
    }

    private String operationsToken() {
        return requestToken(baseForm(OPERATIONS_SCOPE)).path("access_token").asString("");
    }

    private MultiValueMap<String, Object> baseForm(String scope) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("terminal", config.getTerminalId());
        return form;
    }

    private JsonNode requestToken(MultiValueMap<String, Object> form) {
        String body;
        try {
            body = restClient.post().uri(config.getOauthUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new PaymentProviderException("The bank could not be reached", e);
        }
        JsonNode token = parse(body);
        if (token.path("access_token").asString("").isBlank()) {
            throw new PaymentProviderException("The bank did not issue a payment token");
        }
        return token;
    }

    private JsonNode parse(String body) {
        try {
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (JacksonException e) {
            throw new PaymentProviderException("The bank sent an unreadable response", e);
        }
    }
}
