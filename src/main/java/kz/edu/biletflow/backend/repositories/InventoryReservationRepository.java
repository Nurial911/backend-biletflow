package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.InventoryReservation;
import kz.edu.biletflow.backend.entities.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    @Query("select r from InventoryReservation r where r.order.id = :orderId and r.status = kz.edu.biletflow.backend.entities.ReservationStatus.ACTIVE")
    Optional<InventoryReservation> findActiveByOrderId(Long orderId);

    @Query("select coalesce(sum(r.quantity), 0) from InventoryReservation r where r.ticketType.id = :ticketTypeId and r.status = kz.edu.biletflow.backend.entities.ReservationStatus.ACTIVE and r.expiresAt > :now")
    long countActiveQuantity(Long ticketTypeId, LocalDateTime now);

    @Modifying
    @Query("update InventoryReservation r set r.status = :status where r.status = kz.edu.biletflow.backend.entities.ReservationStatus.ACTIVE and r.expiresAt <= :now")
    int expireReservations(LocalDateTime now, ReservationStatus status);
}
