package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventHistoryEntryResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.EventPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

// Organizer event history (SRS 4.16): own events by phase, and each event's activity timeline.
public interface EventHistoryService {
    Page<EventResponse> getOrganizerEvents(Long organizerId, @Nullable EventPhase phase, Pageable pageable);

    // from/to are inclusive dates; actions and entityType narrow the activity type.
    Page<EventHistoryEntryResponse> getEventHistory(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                                    @Nullable LocalDate from, @Nullable LocalDate to,
                                                    @Nullable List<AuditAction> actions, @Nullable String entityType,
                                                    Pageable pageable);
}
