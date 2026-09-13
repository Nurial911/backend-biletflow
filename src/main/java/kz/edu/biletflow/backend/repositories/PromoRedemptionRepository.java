package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PromoRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {
    long countByPromoCodeId(Long promoCodeId);
    boolean existsByOrderId(Long orderId);
}
