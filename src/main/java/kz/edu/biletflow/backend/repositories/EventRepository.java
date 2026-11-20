package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    // Admin events report: events starting in [from, to), with their organizer.
    @Query("SELECT e FROM Event e JOIN FETCH e.organizer WHERE e.startTime >= :from AND e.startTime < :to ORDER BY e.startTime, e.id")
    List<Event> findAllStartingBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Finished events that sold paid tickets and have no payout yet. "Finished" repeats EventPhase.effectiveEnd:
    // the end time, or the end of the start day when there is none (start before today's midnight).
    @Query("""
        SELECT e.id FROM Event e
        WHERE e.status <> kz.edu.biletflow.backend.entities.EventStatus.CANCELLED
          AND ((e.endTime IS NOT NULL AND e.endTime <= :now) OR (e.endTime IS NULL AND e.startTime < :todayStart))
          AND EXISTS (SELECT o.id FROM Order o WHERE o.event = e
                      AND o.status = kz.edu.biletflow.backend.entities.OrderStatus.PAID AND o.totalAmount > 0)
          AND NOT EXISTS (SELECT p.id FROM Payout p WHERE p.event = e)
        ORDER BY e.id
        """)
    List<Long> findFinishedIdsWithoutPayout(@Param("now") LocalDateTime now,
                                            @Param("todayStart") LocalDateTime todayStart,
                                            Pageable pageable);

    long countByStatus(EventStatus status);

    long countBySuspendedTrue();

    Page<Event> findAllByOrganizerId(Long organizerId, Pageable pageable);

    boolean existsByOrganizerId(Long organizerId);

    List<Event> findAllByOrganizerIdOrderByStartTimeDesc(Long organizerId);

    // Row lock: serializes the steps of paid-sales activation for one event.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Query("""
    SELECT e FROM Event e
    WHERE (e.status = kz.edu.biletflow.backend.entities.EventStatus.PUBLISHED
           AND e.visibilityStatus = kz.edu.biletflow.backend.entities.VisibilityStatus.PUBLIC
           AND e.suspended = false)
       OR e.organizer.id = :requesterId
    """)
    Page<Event> findVisibleEvents(@Param("requesterId") Long requesterId, Pageable pageable);

    @Query("""
    SELECT e FROM Event e
    WHERE e.organizer.id = :organizerId
      AND (
           :requesterId = :organizerId
        OR (e.status = kz.edu.biletflow.backend.entities.EventStatus.PUBLISHED
            AND e.visibilityStatus = kz.edu.biletflow.backend.entities.VisibilityStatus.PUBLIC
            AND e.suspended = false)
      )
    """)
    Page<Event> findByOrganizerVisibleTo(@Param("organizerId") Long organizerId,
                                         @Param("requesterId") Long requesterId,
                                         Pageable pageable);
}
