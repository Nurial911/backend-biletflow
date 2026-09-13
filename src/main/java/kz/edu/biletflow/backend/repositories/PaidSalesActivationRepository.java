package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PaidSalesActivation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaidSalesActivationRepository extends JpaRepository<PaidSalesActivation, Long> {
    Optional<PaidSalesActivation> findByEventId(Long eventId);
}
