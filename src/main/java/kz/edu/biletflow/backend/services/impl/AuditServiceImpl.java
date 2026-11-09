package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.AuditLog;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.repositories.AuditLogRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, AuditAction action, Long entityId, @Nullable Long eventId, String description) {
        String actorEmail = userRepository.findById(actorId).map(User::getEmail).orElse(null);
        String text = description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH)
                : description;
        auditLogRepository.save(new AuditLog(actorId, actorEmail, action, entityId, eventId, text));
    }
}
