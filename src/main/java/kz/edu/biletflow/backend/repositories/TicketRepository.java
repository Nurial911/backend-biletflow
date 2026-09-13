package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    long countByTicketTypeIdAndStatusIn(Long ticketTypeId, java.util.Collection<TicketStatus> statuses);
    java.util.List<Ticket> findAllByOrderId(Long orderId);
    java.util.Optional<Ticket> findByTicketIdentifier(String ticketIdentifier);
    long countByEventId(Long eventId);
    long countByEventIdAndStatus(Long eventId, TicketStatus status);
}
