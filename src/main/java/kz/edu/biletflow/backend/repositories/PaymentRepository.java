package kz.edu.biletflow.backend.repositories;

import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    // Admin search: loads each payment's order, event and buyer with the page instead of one query per row.
    @Override
    @EntityGraph(attributePaths = {"order", "order.event", "order.user"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    long countByStatusAndCreatedAtGreaterThanEqual(PaymentStatus status, LocalDateTime since);

    // Admin payments report: every attempt in [from, to), with its order, event and buyer.
    @Query("""
            SELECT p FROM Payment p JOIN FETCH p.order o JOIN FETCH o.event JOIN FETCH o.user
            WHERE p.createdAt >= :from AND p.createdAt < :to
            ORDER BY p.createdAt, p.id
            """)
    List<Payment> findAllCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

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
