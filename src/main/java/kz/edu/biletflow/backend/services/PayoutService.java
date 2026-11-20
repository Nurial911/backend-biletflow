package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.PayoutResponse;
import kz.edu.biletflow.backend.entities.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Simulated organizer payouts (SRS 3.2, 4.10). One per finished event that sold paid tickets: scheduled when the
 * event ends, paid app.payouts.delay-hours later. The amount is the sales API's estimatedPayout at that moment.
 * No provider is called and no money moves.
 */
public interface PayoutService {
    record RunResult(int scheduled, int processed) {
    }

    // Schedules payouts for finished events and pays the due ones. Called by PayoutScheduler (and tests).
    RunResult runPayouts();

    // Organizer's own payouts, newest event first.
    Page<PayoutResponse> getOrganizerPayouts(Long organizerId, @Nullable Payout.Status status, Pageable pageable);

    // Organizer of the event or platform admin; others 404. 404 too while the event has no payout.
    PayoutResponse getEventPayout(Long eventId, Long requesterId, boolean isPlatformAdmin);

    // Platform admin. q: event title, organizer email, reference, or the payout or event id.
    Page<PayoutResponse> searchPayouts(@Nullable String q, @Nullable Payout.Status status, @Nullable Long organizerId,
                                       @Nullable Long eventId, Pageable pageable);

    PayoutResponse getPayout(Long payoutId);

    PayoutResponse holdPayout(Long adminId, Long payoutId, String reason);

    PayoutResponse releasePayout(Long adminId, Long payoutId);

    // Pays now instead of on the scheduled date, if nothing blocks it (otherwise it goes ON_HOLD with the reason).
    PayoutResponse payNow(Long adminId, Long payoutId);

    // Called by refunds inside their transaction: refuses once the event's payout is PAID. Locks the payout row,
    // so a refund and a payout of the same event can't overlap.
    void ensureRefundAllowed(Long eventId);
}
