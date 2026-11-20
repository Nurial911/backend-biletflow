package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.EventSection;
import kz.edu.biletflow.backend.entities.SectionKind;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventSectionRepository extends JpaRepository<EventSection, Long> {
    @EntityGraph(attributePaths = "ticketType")
    List<EventSection> findAllByEventIdOrderBySortOrderAsc(Long eventId);

    @EntityGraph(attributePaths = "ticketType")
    Optional<EventSection> findByIdAndEventId(Long id, Long eventId);

    List<EventSection> findAllByTicketTypeId(Long ticketTypeId);

    boolean existsByTicketTypeId(Long ticketTypeId);

    @Query("""
        SELECT COALESCE(SUM(s.capacity), 0) FROM EventSection s
        WHERE s.ticketType.id = :ticketTypeId AND s.kind = :kind
        """)
    long sumCapacityByTicketType(@Param("ticketTypeId") Long ticketTypeId, @Param("kind") SectionKind kind);

    // Seats go first (EventSeatRepository.deleteAllByEvent); the foreign key would cascade anyway.
    @Modifying
    @Query("DELETE FROM EventSection s WHERE s.event.id = :eventId")
    int deleteAllByEvent(@Param("eventId") Long eventId);
}
