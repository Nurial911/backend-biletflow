package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findAllByOwnerId(Long ownerId, Pageable pageable);

    // Row locks: two scanners reading the same ticket at once must not both admit it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.code = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    long countByEventIdAndStatus(Long eventId, TicketStatus status);

    // q matches part of the attendee email or the exact ticket code; pass "" to list everyone.
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.event.id = :eventId
          AND (LOWER(t.owner.email) LIKE LOWER(CONCAT('%', :q, '%')) OR t.code = :q)
        """)
    Page<Ticket> searchByEvent(@Param("eventId") Long eventId, @Param("q") String q, Pageable pageable);

    @Modifying
    @Query("UPDATE Ticket t SET t.status = :to WHERE t.event.id = :eventId AND t.status = :from")
    int updateStatusByEventId(@Param("eventId") Long eventId,
                              @Param("from") TicketStatus from,
                              @Param("to") TicketStatus to);
}
