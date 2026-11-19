package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.RefundResponse;

import java.util.List;

public interface RefundService {
    // Organizer of the order's event or a platform admin (anyone else: 404). A paid order is refunded in full;
    // a free order has its registration cancelled instead, as there is no money to return.
    OrderResponse refundOrder(Long orderId, Long requesterId, boolean isPlatformAdmin, @Nullable String reason);

    // Buyer, organizer of the event or platform admin (anyone else: 404).
    List<RefundResponse> getOrderRefunds(Long orderId, Long requesterId, boolean isPlatformAdmin);

    // Called by the scheduler: settles bank refunds whose answer was never recorded (e.g. the server stopped
    // during the call). Returns how many were looked at.
    int reconcilePendingRefunds();
}
