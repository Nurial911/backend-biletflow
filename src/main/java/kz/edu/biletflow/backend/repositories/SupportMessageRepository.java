package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
}
