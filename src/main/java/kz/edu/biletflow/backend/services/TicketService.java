package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.TicketResponse;

public interface TicketService {
    TicketResponse cancel(Long userId, Long ticketId);
}
