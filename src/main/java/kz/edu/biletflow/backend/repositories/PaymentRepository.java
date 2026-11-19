package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.Payment;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderIdOrderByIdAsc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, PaymentStatus status);

    // [eventId, sum of processing fees] of payments whose order is in orderStatus (PAID: no fee on refunded orders).
    @Query("""
        SELECT p.order.event.id, SUM(p.processingFee)
        FROM Payment p
        WHERE p.order.event.id IN :eventIds AND p.status = :status AND p.order.status = :orderStatus
        GROUP BY p.order.event.id
        """)
    List<Object[]> sumProcessingFeesByEvent(@Param("eventIds") Collection<Long> eventIds,
                                            @Param("status") PaymentStatus status,
                                            @Param("orderStatus") OrderStatus orderStatus);
}
