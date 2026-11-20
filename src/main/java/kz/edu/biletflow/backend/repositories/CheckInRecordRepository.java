package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.CheckInAction;
import kz.edu.biletflow.backend.entities.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {

    // Records of one action for an event; typeId 0 means every ticket type.
    @Query("""
        SELECT COUNT(r) FROM CheckInRecord r
        WHERE r.event.id = :eventId AND r.action = :action
          AND (:typeId = 0 OR r.ticket.ticketType.id = :typeId)
        """)
    long countByEventAndAction(@Param("eventId") Long eventId,
                               @Param("action") CheckInAction action,
                               @Param("typeId") long typeId);
}
