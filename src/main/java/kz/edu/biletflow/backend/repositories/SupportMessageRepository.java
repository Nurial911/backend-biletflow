package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllBySupportCaseIdOrderByIdAsc(Long caseId);
}
