package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    java.util.List<SupportCase> findAllByUserId(Long userId);
}
