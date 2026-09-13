package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
}
