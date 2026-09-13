package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCodeStringIgnoreCase(String codeString);
}
