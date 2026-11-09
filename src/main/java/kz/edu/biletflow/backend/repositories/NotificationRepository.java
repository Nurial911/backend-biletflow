package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.EmailDeliveryStatus;
import kz.edu.biletflow.backend.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Query("""
        SELECT n.id FROM Notification n
        WHERE n.emailStatus = :status AND n.nextAttemptAt <= :now
        ORDER BY n.id
        """)
    List<Long> findDueEmailIds(@Param("status") EmailDeliveryStatus status,
                               @Param("now") LocalDateTime now,
                               Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
