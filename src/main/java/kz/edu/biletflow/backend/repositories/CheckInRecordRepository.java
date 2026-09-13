package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.CheckInRecord;
import kz.edu.biletflow.backend.entities.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {
    Optional<CheckInRecord> findByTicketIdAndStatus(Long ticketId, CheckInStatus status);
}
