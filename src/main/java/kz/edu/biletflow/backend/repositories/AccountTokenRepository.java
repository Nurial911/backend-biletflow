package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.AccountToken;
import kz.edu.biletflow.backend.entities.AccountTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    // Row lock: two requests with one link must not both use it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountToken t WHERE t.tokenHash = :tokenHash")
    Optional<AccountToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    boolean existsByUserIdAndPurposeAndCreatedAtAfter(Long userId, AccountTokenPurpose purpose, LocalDateTime after);

    // Only the newest link of each kind works.
    @Modifying
    @Query("""
        UPDATE AccountToken t SET t.usedAt = :now
        WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL
        """)
    int invalidateUnused(@Param("userId") Long userId,
                         @Param("purpose") AccountTokenPurpose purpose,
                         @Param("now") LocalDateTime now);
}
