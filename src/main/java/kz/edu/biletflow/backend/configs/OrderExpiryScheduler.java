package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleOrders() {
        int expired = orderService.expireStaleOrders();
        if (expired > 0) {
            log.info("Expired {} unpaid order(s)", expired);
        }
    }
}
