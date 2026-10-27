package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findAllByUserId(Long userId, Pageable pageable);

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
