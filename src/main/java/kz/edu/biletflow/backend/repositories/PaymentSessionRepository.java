package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PaymentSession;
import kz.edu.biletflow.backend.entities.PaymentSessionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {
    Optional<PaymentSession> findByInvoiceId(String invoiceId);

    boolean existsByInvoiceIdEndingWith(String suffix);

    @Query("SELECT s.order.id FROM PaymentSession s WHERE s.id = :id")
    Long findOrderIdById(@Param("id") Long id);

    @Query("SELECT s.id FROM PaymentSession s WHERE s.status IN :statuses ORDER BY s.id")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<PaymentSessionStatus> statuses, Pageable pageable);

    @Query("SELECT s.id FROM PaymentSession s WHERE s.order.id = :orderId AND s.status IN :statuses ORDER BY s.id")
    List<Long> findIdsByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                           @Param("statuses") Collection<PaymentSessionStatus> statuses);
}
