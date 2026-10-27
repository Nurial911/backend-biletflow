package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findAllByOwnerId(Long ownerId, Pageable pageable);
}
