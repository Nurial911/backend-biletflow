package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Refund;
import kz.edu.biletflow.backend.entities.RefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findAllByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, RefundStatus status);

    @Query("SELECT r.order.id FROM Refund r WHERE r.id = :id")
    Long findOrderIdById(@Param("id") Long id);

    // Bank refunds whose outcome was never recorded, oldest first.
    @Query("SELECT r.id FROM Refund r WHERE r.status = :status AND r.createdAt < :before ORDER BY r.id")
    List<Long> findIdsByStatusCreatedBefore(@Param("status") RefundStatus status,
                                            @Param("before") LocalDateTime before,
                                            Pageable pageable);

    // [eventId, sum of refunded amounts]
    @Query("""
        SELECT r.order.event.id, SUM(r.amount)
        FROM Refund r
        WHERE r.order.event.id IN :eventIds AND r.status = :status
        GROUP BY r.order.event.id
        """)
    List<Object[]> sumAmountsByEvent(@Param("eventIds") Collection<Long> eventIds,
                                     @Param("status") RefundStatus status);
}
