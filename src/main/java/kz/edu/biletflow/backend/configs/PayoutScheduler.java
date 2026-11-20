package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.services.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Schedules simulated payouts for events that have ended and pays the ones whose date has come.
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {
    private final PayoutService payoutService;

    @Scheduled(initialDelayString = "${app.payouts.run-delay-ms:300000}",
            fixedDelayString = "${app.payouts.run-delay-ms:300000}")
    public void runPayouts() {
        PayoutService.RunResult result = payoutService.runPayouts();
        if (result.scheduled() > 0 || result.processed() > 0) {
            log.info("Payouts: {} scheduled, {} processed", result.scheduled(), result.processed());
        }
    }
}
