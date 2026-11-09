package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.services.PaymentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Asks the bank about unfinished payments, so a lost result call can't leave money taken and no tickets issued.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "halyk")
public class PaymentSessionScheduler {
    private final PaymentSessionService paymentSessionService;

    @Scheduled(initialDelayString = "${app.payments.halyk.settle-delay-ms:15000}",
            fixedDelayString = "${app.payments.halyk.settle-delay-ms:15000}")
    public void settleOpenSessions() {
        paymentSessionService.settleOpenSessions();
    }
}
