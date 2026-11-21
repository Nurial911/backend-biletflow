package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.configs.HalykConfig;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.RefundResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.PaymentProviderException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.i18n.Text;
import kz.edu.biletflow.backend.mappers.OrderMapper;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.PayoutService;
import kz.edu.biletflow.backend.services.RefundService;
import kz.edu.biletflow.backend.services.impl.HalykClient.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Full refunds of paid orders (SRS 4.9). The tickets stop admitting anyone as soon as a refund starts.
 * Simulated payments are refunded in one transaction. A Halyk payment is returned by the bank: the refund is
 * recorded as PENDING, the bank is called outside any transaction, and its answer is then recorded under the
 * order's row lock. If the answer is lost, {@link #reconcilePendingRefunds()} asks the bank later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private static final String PROVIDER_HALYK = "HALYK";
    private static final String PROVIDER_SIMULATED = "SIMULATED";
    private static final String BANK_REFUNDED = "REFUND";
    private static final int RECONCILE_AFTER_MINUTES = 2; // well past the bank client's 15 s read timeout
    private static final int RECONCILE_BATCH_SIZE = 100;
    private static final int MAX_TEXT_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSessionRepository sessionRepository;
    private final RefundRepository refundRepository;
    private final HalykClient halykClient;
    private final HalykConfig halykConfig;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;
    private final PayoutService payoutService;

    // A refund the bank still has to carry out.
    private record BankRefund(Long refundId, String transactionId) {
    }

    @Override
    public OrderResponse refundOrder(Long orderId, Long requesterId, boolean isPlatformAdmin, @Nullable String reason) {
        String cleanReason = reason == null || reason.isBlank() ? null : shorten(reason.trim());
        BankRefund bankRefund = transactionTemplate.execute(status -> start(orderId, requesterId, isPlatformAdmin, cleanReason));
        if (bankRefund != null) {
            try {
                halykClient.refund(bankRefund.transactionId());
            } catch (PaymentProviderException e) {
                transactionTemplate.executeWithoutResult(status -> fail(bankRefund.refundId(), e.getMessage()));
                throw e;
            }
            transactionTemplate.executeWithoutResult(status -> complete(bankRefund.refundId()));
        }
        return transactionTemplate.execute(status -> orderMapper.toDto(orderRepository.findById(orderId).orElseThrow()));
    }

    @Override
    public List<RefundResponse> getOrderRefunds(Long orderId, Long requesterId, boolean isPlatformAdmin) {
        return transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
            boolean isBuyer = order.getUser().getId().equals(requesterId);
            if (!isBuyer && !isPlatformAdmin && !isOrganizerOf(order, requesterId)) {
                throw notFound(orderId);
            }
            return refundRepository.findAllByOrderIdOrderByIdAsc(orderId).stream().map(orderMapper::toDto).toList();
        });
    }

    @Override
    public int reconcilePendingRefunds() {
        List<Long> ids = refundRepository.findIdsByStatusCreatedBefore(RefundStatus.PENDING,
                LocalDateTime.now().minusMinutes(RECONCILE_AFTER_MINUTES), PageRequest.of(0, RECONCILE_BATCH_SIZE));
        for (Long id : ids) {
            try {
                reconcile(id);
            } catch (PaymentProviderException e) {
                log.warn("Could not check refund {} with the bank: {}", id, e.getMessage()); // tried again next run
            }
        }
        return ids.size();
    }

    // steps //

    // Under the order's row lock. Returns what the bank still has to do, or null when the refund is already complete.
    private BankRefund start(Long orderId, Long requesterId, boolean isPlatformAdmin, @Nullable String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        if (!isPlatformAdmin && !isOrganizerOf(order, requesterId)) {
            throw notFound(orderId);
        }
        if (refundRepository.existsByOrderIdAndStatus(orderId, RefundStatus.PENDING)) {
            throw new BusinessRuleViolationException("A refund of this order is already in progress");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleViolationException("Only a paid order can be refunded, this order is " + order.getStatus());
        }
        // Locked, so a ticket can't be scanned between this check and the status change below.
        List<Ticket> tickets = ticketRepository.findAllByOrderIdForUpdate(orderId);
        long checkedIn = tickets.stream().filter(t -> t.getStatus() == TicketStatus.USED).count();
        if (checkedIn > 0) {
            throw new BusinessRuleViolationException(checkedIn + " ticket(s) of this order were already checked in."
                    + " Undo the check-in first if the order should still be refunded.");
        }

        if (order.getTotalAmount().signum() == 0) {
            cancelRegistration(order, tickets, requesterId, reason);
            return null;
        }

        // Money already paid out to the organizer can't be returned to the buyer (simulated payouts, SRS 3.2).
        // Locks the payout row, so the payout can't be made while this refund is under way.
        payoutService.ensureRefundAllowed(order.getEvent().getId());

        tickets.forEach(t -> t.setStatus(TicketStatus.REFUNDED)); // invalid from now on, even while the bank works
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(orderId, PaymentStatus.SUCCEEDED)
                .orElse(null);
        boolean viaBank = payment != null && PROVIDER_HALYK.equals(payment.getProvider())
                && payment.getProviderTransactionId() != null;

        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setPayment(payment);
        refund.setAmount(order.getTotalAmount());
        refund.setReason(reason);
        refund.setInitiatedById(requesterId);
        if (viaBank) {
            refund.setReference("HALYK-REFUND-" + UUID.randomUUID());
            refund.setProvider(PROVIDER_HALYK);
            refund.setSimulated(halykConfig.isTestMode());
            refund.setStatus(RefundStatus.PENDING);
            Refund saved = refundRepository.save(refund);
            return new BankRefund(saved.getId(), payment.getProviderTransactionId());
        }

        // Simulation only (SRS 3.2): no provider is called and no money moves.
        refund.setReference("SIM-REFUND-" + UUID.randomUUID());
        refund.setProvider(PROVIDER_SIMULATED);
        refund.setSimulated(true);
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setCompletedAt(LocalDateTime.now());
        finishOrder(order, refundRepository.save(refund));
        return null;
    }

    private void complete(Long refundId) {
        Order order = lockOrderOf(refundId);
        Refund refund = refundRepository.findById(refundId).orElseThrow(); // read after the lock: current state
        if (refund.getStatus() != RefundStatus.PENDING) {
            return;
        }
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setCompletedAt(LocalDateTime.now());
        finishOrder(order, refund);
    }

    // The money is still the organizer's, so the order stays PAID and its tickets admit again.
    private void fail(Long refundId, String problem) {
        Order order = lockOrderOf(refundId);
        Refund refund = refundRepository.findById(refundId).orElseThrow();
        if (refund.getStatus() != RefundStatus.PENDING) {
            return;
        }
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(shorten(problem));
        refund.setCompletedAt(LocalDateTime.now());
        TicketStatus restored = order.getEvent().getStatus() == EventStatus.CANCELLED ? TicketStatus.CANCELLED : TicketStatus.VALID;
        ticketRepository.findAllByOrderIdForUpdate(order.getId()).stream()
                .filter(t -> t.getStatus() == TicketStatus.REFUNDED)
                .forEach(t -> t.setStatus(restored));
        auditService.record(refund.getInitiatedById(), AuditAction.REFUND_FAILED, order.getId(), order.getEvent().getId(),
                "Refund of " + refund.getAmount() + " KZT for order #" + order.getId() + " failed: " + problem);
    }

    private void reconcile(Long refundId) {
        String transactionId = transactionTemplate.execute(status -> refundRepository.findById(refundId)
                .filter(r -> r.getStatus() == RefundStatus.PENDING && r.getPayment() != null)
                .map(r -> r.getPayment().getProviderTransactionId())
                .orElse(null));
        if (transactionId == null) {
            return;
        }
        Optional<String> invoiceId = sessionRepository.findFirstByTransactionId(transactionId).map(PaymentSession::getInvoiceId);
        Optional<Transaction> atBank = invoiceId.flatMap(halykClient::findTransaction);
        if (atBank.isPresent() && BANK_REFUNDED.equals(atBank.get().statusName())) {
            transactionTemplate.executeWithoutResult(status -> complete(refundId));
        } else {
            // Never carried out: the organizer can simply ask again.
            transactionTemplate.executeWithoutResult(status -> fail(refundId, "The bank did not confirm the refund"));
        }
    }

    // helpers //

    private void finishOrder(Order order, Refund refund) {
        order.setStatus(OrderStatus.REFUNDED);
        Event event = order.getEvent();
        String label = refund.isSimulated() ? (PROVIDER_HALYK.equals(refund.getProvider()) ? "Halyk ePay test " : "Simulated ") : "";
        auditService.record(refund.getInitiatedById(), AuditAction.ORDER_REFUNDED, order.getId(), event.getId(),
                label + "refund of " + refund.getAmount() + " KZT for order #" + order.getId()
                        + (refund.getReason() != null ? ": " + refund.getReason() : ""));
        // No order id: the email must not attach the (now invalid) tickets.
        notificationService.notifyUser(order.getUser().getId(), NotificationType.REFUND_COMPLETED,
                Text.of("refund.title", event.getTitle()),
                Text.of("refund.body", order.getId(), event.getTitle(), refund.getAmount())
                        .andIf(refund.getReason() != null, "common.reason", refund.getReason())
                        .and("refund.ticketsInvalid")
                        .and(refund.isSimulated() ? "refund.demoNote" : "refund.bankNote"), null);
    }

    private void cancelRegistration(Order order, List<Ticket> tickets, Long actorId, @Nullable String reason) {
        tickets.forEach(t -> t.setStatus(TicketStatus.CANCELLED));
        order.setStatus(OrderStatus.CANCELLED);
        Event event = order.getEvent();
        auditService.record(actorId, AuditAction.REGISTRATION_CANCELLED, order.getId(), event.getId(),
                "Free registration #" + order.getId() + " cancelled" + (reason != null ? ": " + reason : ""));
        notificationService.notifyUser(order.getUser().getId(), NotificationType.REGISTRATION_CANCELLED,
                Text.of("registration.cancelled.title", event.getTitle()),
                Text.of("registration.cancelled.body", order.getId(), event.getTitle())
                        .andIf(reason != null, "common.reason", reason)
                        .and("registration.cancelled.footer"), null);
    }

    private Order lockOrderOf(Long refundId) {
        return orderRepository.findByIdForUpdate(refundRepository.findOrderIdById(refundId)).orElseThrow();
    }

    private static boolean isOrganizerOf(Order order, Long userId) {
        return order.getEvent().getOrganizer().getId().equals(userId);
    }

    // Someone else's order is reported as missing, so its existence isn't revealed.
    private static ResourceNotFoundException notFound(Long orderId) {
        return new ResourceNotFoundException("Order not found with id: " + orderId);
    }

    private static String shorten(String text) {
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
