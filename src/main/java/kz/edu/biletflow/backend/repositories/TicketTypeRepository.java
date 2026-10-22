package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    List<TicketType> findAllByEventIdOrderByIdAsc(Long eventId);

    List<TicketType> findAllByEventIdAndHiddenFalseOrderByIdAsc(Long eventId);

    // Hidden types still reserve inventory, so they are included in the sum.
    // excludeId lets an update ignore the row being edited (use -1 when creating).
    @Query("""
        SELECT COALESCE(SUM(t.quantity), 0) FROM TicketType t
        WHERE t.event.id = :eventId AND t.id <> :excludeId
        """)
    long sumQuantityByEventIdExcluding(@Param("eventId") Long eventId,
                                       @Param("excludeId") Long excludeId);
}