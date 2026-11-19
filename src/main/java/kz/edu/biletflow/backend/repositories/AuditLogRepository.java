package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

// Deliberately not a JpaRepository: audit entries can be added and read but never changed or deleted.
@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog entry);

    // One event's timeline, newest first, within [from, to) and limited to the given actions.
    // Uses idx_audit_log_event_created; the order is fixed here, so pass an unsorted Pageable.
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventId = :eventId
          AND a.createdAt >= :from AND a.createdAt < :to
          AND a.action IN :actions
        ORDER BY a.createdAt DESC, a.id DESC
        """)
    Page<AuditLog> findEventHistory(@Param("eventId") Long eventId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("actions") Collection<String> actions,
                                    Pageable pageable);
}
