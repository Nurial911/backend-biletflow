package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
