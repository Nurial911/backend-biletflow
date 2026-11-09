package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.AuditLog;
import org.springframework.data.repository.Repository;

// Deliberately not a JpaRepository: audit entries can be added but never changed or deleted.
@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog entry);
}
