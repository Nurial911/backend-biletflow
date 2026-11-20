package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.AdminOrderResponse;
import kz.edu.biletflow.backend.dtos.AdminOverviewResponse;
import kz.edu.biletflow.backend.dtos.AdminPaymentResponse;
import kz.edu.biletflow.backend.dtos.AdminRefundResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import kz.edu.biletflow.backend.entities.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

// Platform-admin search and monitoring across all organizers (SRS 4.12). Callers are PLATFORM_ADMIN only.
// In every search, q is matched case-insensitively and literally, and as an exact id when it is a number.
public interface AdminService {
    AdminOverviewResponse getOverview();

    // q: event title, organizer email, or event id. Includes drafts, private and suspended events.
    Page<EventResponse> searchEvents(@Nullable String q, @Nullable EventStatus status, @Nullable Boolean suspended,
                                     @Nullable Long organizerId, Pageable pageable);

    // q: buyer email, attendee name, or order id.
    Page<AdminOrderResponse> searchOrders(@Nullable String q, @Nullable OrderStatus status, @Nullable Long eventId,
                                          Pageable pageable);

    // q: payment reference, buyer email, or order id. from/to are inclusive dates of the attempt.
    Page<AdminPaymentResponse> searchPayments(@Nullable String q, @Nullable PaymentStatus status, @Nullable String provider,
                                              @Nullable LocalDate from, @Nullable LocalDate to, Pageable pageable);

    // q: refund reference, buyer email, or order id.
    Page<AdminRefundResponse> searchRefunds(@Nullable String q, @Nullable RefundStatus status, Pageable pageable);
}
