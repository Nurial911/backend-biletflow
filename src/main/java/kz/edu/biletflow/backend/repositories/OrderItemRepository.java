package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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

    // Per ticket type: [ticketTypeId, sold (PAID qty), reserved (unexpired PENDING qty), revenue (PAID)].
    // Revenue uses the unit price snapshot stored on the item, not the current ticket type price.
    // It is gross: it includes orders refunded later (the refunds are subtracted separately).
    // [ticketTypeId, sold (PAID qty), reserved (unexpired PENDING qty), revenue (PAID + REFUNDED), refunded qty]
    @Query("""
        SELECT i.ticketType.id,
               SUM(CASE WHEN o.status = :paid THEN i.quantity ELSE 0 END),
               SUM(CASE WHEN o.status = :pending AND o.expiresAt > :now THEN i.quantity ELSE 0 END),
               SUM(CASE WHEN o.status = :paid OR o.status = :refunded THEN i.unitPrice * i.quantity ELSE 0 END),
               SUM(CASE WHEN o.status = :refunded THEN i.quantity ELSE 0 END)
        FROM OrderItem i JOIN i.order o
        WHERE o.event.id IN :eventIds
        GROUP BY i.ticketType.id
        """)
    List<Object[]> aggregateSalesByTicketType(@Param("eventIds") Collection<Long> eventIds,
                                              @Param("paid") OrderStatus paid,
                                              @Param("pending") OrderStatus pending,
                                              @Param("refunded") OrderStatus refunded,
                                              @Param("now") LocalDateTime now);

    // Per campaign, PAID orders only: [campaignId, tickets, gross revenue before discount].
    @Query("""
        SELECT o.promoCampaign.id, SUM(i.quantity), SUM(i.unitPrice * i.quantity)
        FROM OrderItem i JOIN i.order o
        WHERE o.promoCampaign.id IN :campaignIds AND o.status = :paid
        GROUP BY o.promoCampaign.id
        """)
    List<Object[]> aggregatePaidByCampaign(@Param("campaignIds") Collection<Long> campaignIds,
                                           @Param("paid") OrderStatus paid);

    // Sales of one event by payment date, including orders refunded later: [date, paidOrders, ticketsSold, revenue].
    // Payments in [from, to); typeId 0 = all ticket types, otherwise only that type's items (and orders containing it).
    @Query("""
        SELECT cast(o.paidAt as LocalDate),
               COUNT(DISTINCT o.id),
               SUM(i.quantity),
               SUM(i.unitPrice * i.quantity)
        FROM OrderItem i JOIN i.order o
        WHERE o.event.id = :eventId AND o.status IN :statuses AND o.paidAt IS NOT NULL
          AND o.paidAt >= :from AND o.paidAt < :to
          AND (:typeId = 0 OR i.ticketType.id = :typeId)
        GROUP BY cast(o.paidAt as LocalDate)
        ORDER BY cast(o.paidAt as LocalDate)
        """)
    List<Object[]> dailyPaidSales(@Param("eventId") Long eventId,
                                  @Param("statuses") Collection<OrderStatus> statuses,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("typeId") long typeId);
    // Seats claimed by a PAID order or an unexpired PENDING one: [seatId, order status, buyer id].
    @Query("""
        SELECT i.eventSeat.id, o.status, o.user.id FROM OrderItem i JOIN i.order o
        WHERE i.eventSeat.id IN :seatIds
          AND (o.status = :paid OR (o.status = :pending AND o.expiresAt > :now))
        """)
    List<Object[]> findSeatClaims(@Param("seatIds") Collection<Long> seatIds,
                                  @Param("paid") OrderStatus paid,
                                  @Param("pending") OrderStatus pending,
                                  @Param("now") LocalDateTime now);

    // Same for every seat of one section.
    @Query("""
        SELECT i.eventSeat.id, o.status, o.user.id FROM OrderItem i JOIN i.order o
        WHERE i.eventSection.id = :sectionId AND i.eventSeat IS NOT NULL
          AND (o.status = :paid OR (o.status = :pending AND o.expiresAt > :now))
        """)
    List<Object[]> findSeatClaimsInSection(@Param("sectionId") Long sectionId,
                                           @Param("paid") OrderStatus paid,
                                           @Param("pending") OrderStatus pending,
                                           @Param("now") LocalDateTime now);

    // Per section of an event: [sectionId, sold (PAID qty), held (unexpired PENDING qty)].
    @Query("""
        SELECT i.eventSection.id,
               SUM(CASE WHEN o.status = :paid THEN i.quantity ELSE 0 END),
               SUM(CASE WHEN o.status = :pending AND o.expiresAt > :now THEN i.quantity ELSE 0 END)
        FROM OrderItem i JOIN i.order o
        WHERE o.event.id = :eventId AND i.eventSection IS NOT NULL
        GROUP BY i.eventSection.id
        """)
    List<Object[]> aggregateBySection(@Param("eventId") Long eventId,
                                      @Param("paid") OrderStatus paid,
                                      @Param("pending") OrderStatus pending,
                                      @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(i) > 0 FROM OrderItem i JOIN i.order o
        WHERE i.eventSection.id = :sectionId
          AND (o.status = :paid OR (o.status = :pending AND o.expiresAt > :now))
        """)
    boolean existsClaimInSection(@Param("sectionId") Long sectionId,
                                 @Param("paid") OrderStatus paid,
                                 @Param("pending") OrderStatus pending,
                                 @Param("now") LocalDateTime now);
}
