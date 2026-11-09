package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.PromoCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, Long> {
    List<PromoCampaign> findAllByEventIdOrderByIdDesc(Long eventId);

    Optional<PromoCampaign> findByIdAndEventId(Long id, Long eventId);

    Optional<PromoCampaign> findByEventIdAndCode(Long eventId, String code);

    Optional<PromoCampaign> findByEventIdAndToken(Long eventId, String token);

    boolean existsByCode(String code);

    // Row lock: orders using one campaign run one after another, so its limit can't be exceeded.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PromoCampaign c WHERE c.event.id = :eventId AND c.code = :code")
    Optional<PromoCampaign> findByEventIdAndCodeForUpdate(@Param("eventId") Long eventId, @Param("code") String code);
}
