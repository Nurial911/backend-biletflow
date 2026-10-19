package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findAllByOrganizerId(Long organizerId, Pageable pageable);

    @Query("""
        SELECT e FROM Event e
        WHERE (e.status = kz.edu.biletflow.backend.entities.EventStatus.PUBLISHED 
               AND e.visibilityStatus = kz.edu.biletflow.backend.entities.VisibilityStatus.PUBLIC)
                      OR (:requesterId IS NOT NULL AND e.organizer.id = :requesterId)
       """)
    Page<Event> findVisibleEvents(@Param("requesterId") Long requesterId, Pageable pageable);

    @Query("""
        SELECT e FROM Event e
        WHERE e.organizer.id = :organizerId
          AND (
               (:requesterId IS NOT NULL AND :requesterId = :organizerId)
            OR (e.status = kz.edu.biletflow.backend.entities.EventStatus.PUBLISHED
                AND e.visibilityStatus = kz.edu.biletflow.backend.entities.VisibilityStatus.PUBLIC)
          )
        """)
    Page<Event> findByOrganizerVisibleTo(@Param("organizerId") Long organizerId,
                                         @Param("requesterId") Long requesterId,
                                         Pageable pageable);
}
