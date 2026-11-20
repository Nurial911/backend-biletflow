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

import java.util.Collection;
import java.util.List;
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

    // All tickets of an order, locked so a refund and a scan of one of them run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.order.id = :orderId ORDER BY t.id")
    List<Ticket> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);

    List<Ticket> findAllByOrderIdAndStatusInOrderByIdAsc(Long orderId, Collection<TicketStatus> statuses);

    long countByEventIdAndStatus(Long eventId, TicketStatus status);

    // Attendance per ticket type, over tickets that grant entry (VALID + USED):
    // [ticketTypeId, holders, checkedIn, firstArrival, lastArrival].
    @Query("""
        SELECT t.ticketType.id, COUNT(t),
               SUM(CASE WHEN t.status = kz.edu.biletflow.backend.entities.TicketStatus.USED THEN 1 ELSE 0 END),
               MIN(t.usedAt), MAX(t.usedAt)
        FROM Ticket t
        WHERE t.event.id = :eventId
          AND t.status IN (kz.edu.biletflow.backend.entities.TicketStatus.VALID,
                           kz.edu.biletflow.backend.entities.TicketStatus.USED)
        GROUP BY t.ticketType.id
        """)
    List<Object[]> aggregateAttendanceByTicketType(@Param("eventId") Long eventId);

    // Checked-in tickets by arrival interval, oldest first: [intervalStart, count]. Intervals are aligned to
    // midnight (origin 2000-01-01). typeId 0 means every ticket type.
    @Query(value = """
        SELECT date_bin(make_interval(mins => :minutes), t.used_at, TIMESTAMP '2000-01-01 00:00:00') AS bucket,
               COUNT(*)
        FROM tickets t
        WHERE t.event_id = :eventId AND t.status = 'USED' AND t.used_at IS NOT NULL
          AND (:typeId = 0 OR t.ticket_type_id = :typeId)
        GROUP BY bucket
        ORDER BY bucket
        """, nativeQuery = true)
    List<Object[]> countArrivalsByInterval(@Param("eventId") Long eventId,
                                           @Param("typeId") long typeId,
                                           @Param("minutes") int minutes);


    // Whether the user holds any ticket for the event, in any status (calendar files of cancelled events).
    boolean existsByEventIdAndOwnerId(Long eventId, Long ownerId);

    // Everyone holding a ticket in this status, for event-wide notifications.
    @Query("SELECT DISTINCT t.owner.id FROM Ticket t WHERE t.event.id = :eventId AND t.status = :status")
    List<Long> findOwnerIdsByEvent(@Param("eventId") Long eventId, @Param("status") TicketStatus status);

    // q matches part of the attendee email or name, or the exact ticket code; pass "" to list everyone.
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.event.id = :eventId
          AND (LOWER(t.owner.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.attendeeName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR t.code = :q)
        """)
    Page<Ticket> searchByEvent(@Param("eventId") Long eventId, @Param("q") String q, Pageable pageable);

    @Modifying
    @Query("UPDATE Ticket t SET t.status = :to WHERE t.event.id = :eventId AND t.status = :from")
    int updateStatusByEventId(@Param("eventId") Long eventId,
                              @Param("from") TicketStatus from,
                              @Param("to") TicketStatus to);
}
