package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportCaseRepository extends JpaRepository<SupportCase, Long>, JpaSpecificationExecutor<SupportCase> {
}
