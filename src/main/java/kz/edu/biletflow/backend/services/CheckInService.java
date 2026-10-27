package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CheckInResponse;
import kz.edu.biletflow.backend.dtos.CheckInStatsResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CheckInService {
    Page<EventResponse> getAssignedEvents(Long staffId, Pageable pageable);

    CheckInResponse scan(Long eventId, String code, Long staffId, boolean isPlatformAdmin);

    TicketResponse undo(Long eventId, Long ticketId, Long staffId, boolean isPlatformAdmin);

    Page<TicketResponse> searchAttendees(Long eventId, String query, Long staffId, boolean isPlatformAdmin, Pageable pageable);

    CheckInStatsResponse getStats(Long eventId, Long staffId, boolean isPlatformAdmin);
}
