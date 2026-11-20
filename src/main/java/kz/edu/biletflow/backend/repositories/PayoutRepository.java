package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long>, JpaSpecificationExecutor<Payout> {
    @Override
    @EntityGraph(attributePaths = {"event", "organizer"})
    Page<Payout> findAll(Specification<Payout> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "organizer"})
    Optional<Payout> findWithEventById(Long id);

    @EntityGraph(attributePaths = {"event", "organizer"})
    Optional<Payout> findByEventId(Long eventId);

    // Row lock: paying a payout and refunding an order of its event never overlap.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.event.id = :eventId")
    Optional<Payout> findByEventIdForUpdate(@Param("eventId") Long eventId);

    // Payouts whose date has come, and automatic holds to check again (an admin hold waits for an admin).
    @Query("""
        SELECT p.id FROM Payout p
        WHERE (p.status = :scheduled AND p.scheduledFor <= :now)
           OR (p.status = :onHold AND p.holdType <> :adminHold)
        ORDER BY p.scheduledFor, p.id
        """)
    List<Long> findIdsToProcess(@Param("scheduled") Payout.Status scheduled,
                                @Param("onHold") Payout.Status onHold,
                                @Param("adminHold") Payout.HoldType adminHold,
                                @Param("now") LocalDateTime now,
                                Pageable pageable);

    long countByStatus(Payout.Status status);

    @EntityGraph(attributePaths = {"event", "organizer"})
    @Query("SELECT p FROM Payout p WHERE p.scheduledFor >= :from AND p.scheduledFor < :to ORDER BY p.scheduledFor, p.id")
    List<Payout> findAllScheduledBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
