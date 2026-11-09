package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.PostConstruct;
import kz.edu.biletflow.backend.configs.HalykConfig;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentSessionResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.PaymentProviderException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.PaymentSessionRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.OrderService;
import kz.edu.biletflow.backend.services.PaymentSessionService;
import kz.edu.biletflow.backend.services.impl.HalykClient.Transaction;
import kz.edu.biletflow.backend.services.impl.OrderFulfillment.PaymentDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Card payments on Halyk's hosted page (card numbers never reach this server).
 * The bank's status API is the only source of truth: the result call just prompts a check, so a forged
 * call can change nothing. The shopper's money is first held by the bank (AUTH). It is captured only after
 * the order is confirmed sellable, and otherwise released. Bank calls happen outside database transactions;
 * each decision then re-checks the state under the order's row lock, so repeated calls settle a session once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSessionServiceImpl implements PaymentSessionService {
    private static final String PROVIDER_HALYK = "HALYK";
    private static final String STATUS_HELD = "AUTH";
    private static final String STATUS_TAKEN = "CHARGE";
    private static final String STATUS_DECLINED = "REJECT";
    private static final Set<PaymentSessionStatus> UNSETTLED = Set.of(
            PaymentSessionStatus.OPEN, PaymentSessionStatus.CAPTURING, PaymentSessionStatus.RELEASE_DUE);
    private static final int SETTLE_BATCH_SIZE = 100;
    private static final int ABANDON_GRACE_MINUTES = 2; // a payment can land just as the page runs out
    private static final int MAX_REASON_LENGTH = 500;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderFulfillment fulfillment;
    private final HalykClient halykClient;
    private final HalykConfig halykConfig;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Value("${app.payments.provider:simulated}")
    private String provider;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.orders.hold-minutes:15}")
    private int holdMinutes;

    private record NewSession(Long id, String invoiceId, String secretHash, BigDecimal amount, String buyerEmail) {
    }

    @PostConstruct
    void checkConfiguration() {
        if (isHalyk() && (halykConfig.getClientId().isBlank() || halykConfig.getClientSecret().isBlank()
                || halykConfig.getTerminalId().isBlank())) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER=halyk needs HALYK_CLIENT_ID, HALYK_CLIENT_SECRET and HALYK_TERMINAL_ID");
        }
    }

    @Override
    public PaymentSessionResponse openSession(Long userId, Long orderId) {
        if (!isHalyk()) {
            return new PaymentSessionResponse("SIMULATED", true, null, null);
        }
        NewSession session = transactionTemplate.execute(status -> createSession(userId, orderId));
        JsonNode token;
        try {
            token = halykClient.createPaymentToken(session.invoiceId(), session.secretHash(), session.amount(), callbackUrl());
        } catch (PaymentProviderException e) {
            transactionTemplate.executeWithoutResult(status -> sessionRepository.findById(session.id())
                    .ifPresent(s -> s.setStatus(PaymentSessionStatus.ABANDONED)));
            throw e;
        }
        return new PaymentSessionResponse(PROVIDER_HALYK, halykConfig.isTestMode(), halykConfig.getScriptUrl(),
                paymentObject(orderId, session, token));
    }

    @Override
    public OrderResponse checkPayment(Long orderId, Long requesterId, boolean isPlatformAdmin) {
        orderService.getOrderById(orderId, requesterId, isPlatformAdmin); // 404 unless they may see it
        if (isHalyk()) {
            sessionRepository.findIdsByOrderIdAndStatusIn(orderId, UNSETTLED).forEach(this::settleIgnoringOutage);
        }
        return orderService.getOrderById(orderId, requesterId, isPlatformAdmin);
    }

    @Override
    public void handleHalykCallback(String body) {
        if (!isHalyk()) {
            throw new ResourceNotFoundException("Not found");
        }
        JsonNode json;
        try {
            json = jsonMapper.readTree(body == null ? "{}" : body);
        } catch (JacksonException e) {
            throw new BusinessRuleViolationException("Malformed payment notification");
        }
        PaymentSession session = sessionRepository.findByInvoiceId(json.path("invoiceId").asString(""))
                .orElseThrow(() -> new ResourceNotFoundException("Unknown payment session"));
        if (!sameSecret(json.path("secret_hash").asString(""), session.getSecretHash())) {
            log.warn("Rejected a payment notification with a wrong secret for session {}", session.getId());
            throw new ForbiddenOperationException("Invalid payment notification");
        }
        settle(session.getId());
    }

    @Override
    public int settleOpenSessions() {
        List<Long> ids = sessionRepository.findIdsByStatusIn(UNSETTLED, PageRequest.of(0, SETTLE_BATCH_SIZE));
        ids.forEach(this::settleIgnoringOutage);
        return ids.size();
    }

    // settling //

    // A bank outage just means trying again on the next run.
    private void settleIgnoringOutage(Long sessionId) {
        try {
            settle(sessionId);
        } catch (PaymentProviderException e) {
            log.warn("Could not settle payment session {}: {}", sessionId, e.getMessage());
        }
    }

    private void settle(Long sessionId) {
        PaymentSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !UNSETTLED.contains(session.getStatus())) {
            return;
        }
        Optional<Transaction> found = halykClient.findTransaction(session.getInvoiceId());
        if (session.getStatus() == PaymentSessionStatus.RELEASE_DUE) {
            found.ifPresent(transaction -> release(sessionId, transaction));
            return;
        }
        if (found.isEmpty()) {
            abandonIfOverdue(sessionId);
            return;
        }
        Transaction transaction = found.get();
        switch (transaction.statusName()) {
            case STATUS_HELD -> capture(sessionId, transaction);
            case STATUS_TAKEN -> finish(sessionId, transaction);
            case STATUS_DECLINED -> {
                transactionTemplate.executeWithoutResult(status -> recordDecline(sessionId, transaction));
                abandonIfOverdue(sessionId);
            }
            default -> abandonIfOverdue(sessionId);
        }
    }

    private void capture(Long sessionId, Transaction transaction) {
        Boolean accepted = transactionTemplate.execute(status -> acceptForCapture(sessionId, transaction));
        if (Boolean.TRUE.equals(accepted)) {
            halykClient.charge(transaction.id());
            finish(sessionId, transaction);
        } else {
            release(sessionId, transaction);
        }
    }

    private boolean acceptForCapture(Long sessionId, Transaction transaction) {
        Order order = lockOrderOf(sessionId);
        PaymentSession session = sessionRepository.findById(sessionId).orElseThrow();
        if (session.getStatus() == PaymentSessionStatus.CAPTURING) {
            return true;
        }
        if (session.getStatus() != PaymentSessionStatus.OPEN) {
            return false;
        }
        String problem = whyNotAcceptable(order, transaction);
        if (problem != null) {
            markForRelease(session, order, transaction, problem);
            return false;
        }
        session.setStatus(PaymentSessionStatus.CAPTURING);
        session.setTransactionId(transaction.id());
        return true;
    }

    private void finish(Long sessionId, Transaction transaction) {
        transactionTemplate.executeWithoutResult(status -> completeOrRelease(sessionId, transaction));
        PaymentSession session = sessionRepository.findById(sessionId).orElseThrow();
        if (session.getStatus() == PaymentSessionStatus.RELEASE_DUE) {
            // Status is re-read because the snapshot may still say the money is only held.
            halykClient.findTransaction(session.getInvoiceId()).ifPresent(latest -> release(sessionId, latest));
        }
    }

    private void completeOrRelease(Long sessionId, Transaction transaction) {
        Order order = lockOrderOf(sessionId);
        PaymentSession session = sessionRepository.findById(sessionId).orElseThrow();
        if (session.getStatus() != PaymentSessionStatus.OPEN && session.getStatus() != PaymentSessionStatus.CAPTURING) {
            return;
        }
        String problem = whyNotAcceptable(order, transaction);
        if (problem != null) {
            markForRelease(session, order, transaction, problem);
            return;
        }
        session.setStatus(PaymentSessionStatus.PAID);
        session.setTransactionId(transaction.id());
        fulfillment.markPaid(order, LocalDateTime.now());
        orderRepository.save(order);
        Payment payment = fulfillment.savePayment(order, new PaymentDetails("HALYK-" + transaction.id(), PaymentStatus.SUCCEEDED,
                fulfillment.processingFee(order.getTotalAmount()), null, PROVIDER_HALYK, transaction.id(), halykConfig.isTestMode()));
        fulfillment.issueTickets(order, halykConfig.isTestMode());
        auditService.record(order.getUser().getId(), AuditAction.PAYMENT_SUCCEEDED, payment.getId(), order.getEvent().getId(),
                "Halyk ePay" + testLabel() + " payment of " + order.getTotalAmount() + " KZT for order #" + order.getId());
    }

    // The bank holds or took money for something we can no longer sell, for example a cancelled or expired order.
    private void markForRelease(PaymentSession session, Order order, Transaction transaction, String reason) {
        session.setStatus(PaymentSessionStatus.RELEASE_DUE);
        session.setTransactionId(transaction.id());
        session.setReleaseReason(shorten(reason));
        Payment payment = fulfillment.savePayment(order, new PaymentDetails("HALYK-RELEASED-" + transaction.id(), PaymentStatus.FAILED,
                BigDecimal.ZERO, shorten(reason), PROVIDER_HALYK, transaction.id(), halykConfig.isTestMode()));
        auditService.record(order.getUser().getId(), AuditAction.PAYMENT_FAILED, payment.getId(), order.getEvent().getId(),
                "Halyk ePay" + testLabel() + " payment for order #" + order.getId() + " not accepted: " + reason);
        notificationService.notifyUser(order.getUser().getId(), NotificationType.PAYMENT_FAILED,
                "Payment not accepted for " + order.getEvent().getTitle(),
                "We could not accept your payment for order #" + order.getId() + ": " + reason
                        + ". No tickets were issued and the money is being returned to your card.", null);
    }

    private void release(Long sessionId, Transaction transaction) {
        switch (transaction.statusName()) {
            case STATUS_HELD -> halykClient.cancel(transaction.id());
            case STATUS_TAKEN -> halykClient.refund(transaction.id());
            default -> { /* already cancelled or refunded at the bank */ }
        }
        transactionTemplate.executeWithoutResult(status -> sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == PaymentSessionStatus.RELEASE_DUE) {
                session.setStatus(PaymentSessionStatus.RELEASED);
            }
        }));
    }

    // The bank's page allows several attempts, so a decline doesn't end the session.
    private void recordDecline(Long sessionId, Transaction transaction) {
        Order order = lockOrderOf(sessionId);
        PaymentSession session = sessionRepository.findById(sessionId).orElseThrow();
        if (session.getStatus() != PaymentSessionStatus.OPEN || transaction.id().equals(session.getLastDeclinedTransactionId())) {
            return;
        }
        session.setLastDeclinedTransactionId(transaction.id());
        Payment payment = fulfillment.savePayment(order, new PaymentDetails("HALYK-DECLINED-" + transaction.id(), PaymentStatus.FAILED,
                BigDecimal.ZERO, shorten("Declined by the bank: " + transaction.reason()), PROVIDER_HALYK, transaction.id(), halykConfig.isTestMode()));
        auditService.record(order.getUser().getId(), AuditAction.PAYMENT_FAILED, payment.getId(), order.getEvent().getId(),
                "Halyk ePay" + testLabel() + " payment for order #" + order.getId() + " declined");
        notificationService.notifyUser(order.getUser().getId(), NotificationType.PAYMENT_FAILED,
                "Payment failed for " + order.getEvent().getTitle(),
                "The bank declined the payment for order #" + order.getId() + ", so no tickets were issued."
                        + " You can try again until the reservation expires.", null);
    }

    private void abandonIfOverdue(Long sessionId) {
        transactionTemplate.executeWithoutResult(status -> sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == PaymentSessionStatus.OPEN
                    && LocalDateTime.now().isAfter(session.getExpiresAt().plusMinutes(ABANDON_GRACE_MINUTES))) {
                session.setStatus(PaymentSessionStatus.ABANDONED);
            }
        }));
    }

    // helpers //

    private NewSession createSession(Long userId, Long orderId) {
        // Someone else's order is reported as missing, so its existence isn't revealed.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        LocalDateTime now = LocalDateTime.now();
        String problem = whyNotPayable(order, now);
        if (problem != null) {
            throw new BusinessRuleViolationException(problem);
        }

        // The reservation has to outlast the bank's page, but repeat visits can't hold tickets forever.
        int sessionMinutes = halykConfig.getSessionMinutes();
        LocalDateTime wanted = now.plusMinutes(sessionMinutes);
        LocalDateTime latest = order.getCreatedAt().plusMinutes((long) holdMinutes + sessionMinutes);
        LocalDateTime extended = wanted.isAfter(latest) ? latest : wanted;
        if (extended.isAfter(order.getExpiresAt())) {
            order.setExpiresAt(extended);
        }

        PaymentSession session = new PaymentSession();
        session.setOrder(order);
        session.setInvoiceId(newInvoiceId());
        session.setSecretHash(newSecret());
        session.setExpiresAt(wanted);
        PaymentSession saved = sessionRepository.save(session);
        return new NewSession(saved.getId(), saved.getInvoiceId(), saved.getSecretHash(),
                new BigDecimal(order.getTotalAmount().stripTrailingZeros().toPlainString()), order.getUser().getEmail());
    }

    private Order lockOrderOf(Long sessionId) {
        return orderRepository.findByIdForUpdate(sessionRepository.findOrderIdById(sessionId)).orElseThrow();
    }

    private String whyNotPayable(Order order, LocalDateTime now) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return "Only a pending order can be paid, this order is " + order.getStatus();
        }
        if (!order.getExpiresAt().isAfter(now)) {
            return "The order has expired, please create a new one";
        }
        if (order.getEvent().getStatus() != EventStatus.PUBLISHED || order.getEvent().isSuspended()) {
            return "The event is no longer available";
        }
        if (!order.getEvent().isPaidSalesActive()) {
            return "Paid sales for this event are suspended";
        }
        return null;
    }

    private String whyNotAcceptable(Order order, Transaction transaction) {
        String problem = whyNotPayable(order, LocalDateTime.now());
        if (problem != null) {
            return problem;
        }
        if (transaction.amount().compareTo(order.getTotalAmount()) != 0 || !"KZT".equals(transaction.currency())) {
            return "The amount paid does not match the order";
        }
        if (!halykConfig.getTerminalId().equals(transaction.terminalId())) {
            return "The payment was made to a different merchant";
        }
        return null;
    }

    private Map<String, Object> paymentObject(Long orderId, NewSession session, JsonNode token) {
        String frontend = withoutTrailingSlash(frontendUrl);
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("invoiceId", session.invoiceId());
        payment.put("backLink", frontend + "/orders/" + orderId + "/payment-result?status=success");
        payment.put("failureBackLink", frontend + "/orders/" + orderId + "/payment-result?status=failure");
        payment.put("postLink", callbackUrl());
        payment.put("failurePostLink", callbackUrl());
        payment.put("language", "rus");
        payment.put("description", "BiletFlow order #" + orderId);
        payment.put("terminal", halykConfig.getTerminalId());
        payment.put("amount", session.amount());
        payment.put("currency", "KZT");
        payment.put("email", session.buyerEmail()); // the bank's page requires an email or a phone
        payment.put("auth", token);
        return payment;
    }

    private String callbackUrl() {
        String base = halykConfig.getCallbackBaseUrl().isBlank() ? frontendUrl : halykConfig.getCallbackBaseUrl();
        return withoutTrailingSlash(base) + "/api/payments/halyk/callback";
    }

    // 15 digits. Halyk requires the last 6 to be unique on their own.
    private String newInvoiceId() {
        String id;
        do {
            id = String.format("%09d%06d", RANDOM.nextInt(1_000_000_000), RANDOM.nextInt(1_000_000));
        } while (sessionRepository.existsByInvoiceIdEndingWith(id.substring(9)));
        return id;
    }

    private static String newSecret() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static boolean sameSecret(String received, String expected) {
        return MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String shorten(String text) {
        return text.length() > MAX_REASON_LENGTH ? text.substring(0, MAX_REASON_LENGTH) : text;
    }

    private static String withoutTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }

    private String testLabel() {
        return halykConfig.isTestMode() ? " test" : "";
    }

    private boolean isHalyk() {
        return "halyk".equalsIgnoreCase(provider);
    }
}
