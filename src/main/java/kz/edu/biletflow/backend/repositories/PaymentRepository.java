package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Payment;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderIdOrderByIdAsc(Long orderId);

    // [eventId, sum of processing fees]
    @Query("""
        SELECT p.order.event.id, SUM(p.processingFee)
        FROM Payment p
        WHERE p.order.event.id IN :eventIds AND p.status = :status
        GROUP BY p.order.event.id
        """)
    List<Object[]> sumProcessingFeesByEvent(@Param("eventIds") Collection<Long> eventIds,
                                            @Param("status") PaymentStatus status);
}
