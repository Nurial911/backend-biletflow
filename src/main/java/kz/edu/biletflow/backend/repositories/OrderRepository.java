package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Page<Order> findAllByUserId(Long userId, Pageable pageable);

    // Row lock: two payment requests for one order must not both see it PENDING and issue tickets.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // Per event: [eventId, paid, pending (still holding inventory), cancelled, expired].
    // A PENDING order past its hold counts as expired even before the scheduler flips it.
    @Query("""
        SELECT o.event.id,
               SUM(CASE WHEN o.status = :paid THEN 1 ELSE 0 END),
               SUM(CASE WHEN o.status = :pending AND o.expiresAt > :now THEN 1 ELSE 0 END),
               SUM(CASE WHEN o.status = :cancelled THEN 1 ELSE 0 END),
               SUM(CASE WHEN o.status = :expired OR (o.status = :pending AND o.expiresAt <= :now) THEN 1 ELSE 0 END)
        FROM Order o
        WHERE o.event.id IN :eventIds
        GROUP BY o.event.id
        """)
    List<Object[]> countOrdersByEvent(@Param("eventIds") Collection<Long> eventIds,
                                      @Param("paid") OrderStatus paid,
                                      @Param("pending") OrderStatus pending,
                                      @Param("cancelled") OrderStatus cancelled,
                                      @Param("expired") OrderStatus expired,
                                      @Param("now") LocalDateTime now);

    // [orderId, buyerId, buyerEmail]; the left join leaves the email null for soft-deleted buyers
    // (loading them through o.user would fail, @SQLRestriction hides the row).
    @Query("SELECT o.id, o.user.id, u.email FROM Order o LEFT JOIN o.user u WHERE o.id IN :orderIds")
    List<Object[]> findBuyers(@Param("orderIds") Collection<Long> orderIds);

    // Releases the inventory of unpaid orders (queries that count "sold" ignore EXPIRED).
    @Modifying
    @Query("UPDATE Order o SET o.status = :expired WHERE o.status = :pending AND o.expiresAt <= :now")
    int expirePending(@Param("pending") OrderStatus pending,
                      @Param("expired") OrderStatus expired,
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = :to WHERE o.event.id = :eventId AND o.status = :from")
    int updateStatusByEventId(@Param("eventId") Long eventId,
                              @Param("from") OrderStatus from,
                              @Param("to") OrderStatus to);
}
