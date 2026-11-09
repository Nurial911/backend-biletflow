package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
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
