package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Sold = PAID orders + PENDING orders whose hold hasn't run out yet (they still reserve inventory).
    @Query("""
        SELECT COALESCE(SUM(i.quantity), 0) FROM OrderItem i
        WHERE i.ticketType.id = :ticketTypeId
          AND (i.order.status = :paid OR (i.order.status = :pending AND i.order.expiresAt > :now))
        """)
    long countSoldOrReserved(@Param("ticketTypeId") Long ticketTypeId,
                             @Param("paid") OrderStatus paid,
                             @Param("pending") OrderStatus pending,
                             @Param("now") LocalDateTime now);
}
