package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.EventSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {
    @Query("""
        SELECT s FROM EventSeat s JOIN FETCH s.section sec LEFT JOIN FETCH sec.ticketType
        WHERE s.event.id = :eventId AND s.id IN :ids
        """)
    List<EventSeat> findAllForEvent(@Param("eventId") Long eventId, @Param("ids") Collection<Long> ids);

    // Row locks in id order, the same order on every request, so two orders can't deadlock.
    // Taken after the ticket type locks (see OrderServiceImpl).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EventSeat s WHERE s.id IN :ids ORDER BY s.id")
    List<EventSeat> lockAll(@Param("ids") Collection<Long> ids);

    // A seat's current state, read straight from the database rather than the persistence context, so it is
    // accurate right after the seat rows were locked: [seatId, blocked, ticketTypeId or null, ticket type hidden or null].
    @Query("""
        SELECT s.id, s.blocked, t.id, t.hidden
        FROM EventSeat s JOIN s.section sec LEFT JOIN sec.ticketType t
        WHERE s.id IN :ids
        """)
    List<Object[]> findStates(@Param("ids") Collection<Long> ids);

    List<EventSeat> findAllBySectionIdOrderByIdAsc(Long sectionId);

    // Per section: [sectionId, seats, blocked seats].
    @Query("""
        SELECT s.section.id, COUNT(s), SUM(CASE WHEN s.blocked = true THEN 1 ELSE 0 END)
        FROM EventSeat s WHERE s.event.id = :eventId
        GROUP BY s.section.id
        """)
    List<Object[]> countBySection(@Param("eventId") Long eventId);

    @Query("""
        SELECT COUNT(s) FROM EventSeat s
        WHERE s.section.ticketType.id = :ticketTypeId AND s.blocked = false
        """)
    long countOnSaleByTicketType(@Param("ticketTypeId") Long ticketTypeId);

    // Per event: [eventId, blocked seats]; events without blocked seats are missing.
    @Query("""
        SELECT s.event.id, COUNT(s) FROM EventSeat s
        WHERE s.event.id IN :eventIds AND s.blocked = true
        GROUP BY s.event.id
        """)
    List<Object[]> countBlockedByEvent(@Param("eventIds") Collection<Long> eventIds);

    @Query("SELECT s FROM EventSeat s JOIN FETCH s.section WHERE s.event.id = :eventId AND s.blocked = true")
    List<EventSeat> findBlockedByEvent(@Param("eventId") Long eventId);

    @Modifying
    @Query("DELETE FROM EventSeat s WHERE s.event.id = :eventId")
    int deleteAllByEvent(@Param("eventId") Long eventId);
}
