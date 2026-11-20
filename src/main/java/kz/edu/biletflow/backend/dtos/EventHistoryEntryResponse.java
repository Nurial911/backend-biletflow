package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.User;

import java.time.LocalDateTime;

/**
 * One entry of an event's activity timeline (SRS 4.16), read from the audit log.
 * actorRole is the actor's current role (null if the account was deleted). Platform admins are shown
 * to organizers as "BiletFlow Support" without id or email, as in support cases.
 */
public record EventHistoryEntryResponse(
        Long id,
        LocalDateTime timestamp,
        Long actorId,
        String actorName,
        String actorEmail,
        User.Role actorRole,
        String action,
        String entityType,
        Long entityId,
        String description) {
}
