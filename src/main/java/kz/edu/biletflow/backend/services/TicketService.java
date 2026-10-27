package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {
    Page<TicketResponse> getMyTickets(Long userId, Pageable pageable);

    TicketResponse getTicketById(Long ticketId, Long requesterId, boolean isPlatformAdmin);

    // PNG image of the ticket's QR code.
    byte[] getTicketQr(Long ticketId, Long requesterId, boolean isPlatformAdmin);
}
