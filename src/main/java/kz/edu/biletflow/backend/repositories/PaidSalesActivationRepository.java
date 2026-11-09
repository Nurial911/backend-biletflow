package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PaidSalesActivation;
import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaidSalesActivationRepository extends JpaRepository<PaidSalesActivation, Long> {
    Optional<PaidSalesActivation> findByEventId(Long eventId);

    Page<PaidSalesActivation> findAllByStatus(PaidSalesStatus status, Pageable pageable);
}
