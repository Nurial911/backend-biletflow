package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.entities.AuditAction;

public interface AuditService {
    // Must run inside the transaction of the action it describes, so they commit or roll back together.
    // actorId is null for actions the system takes by itself (schedulers).
    void record(@Nullable Long actorId, AuditAction action, Long entityId, @Nullable Long eventId, String description);
}
