package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
