package kz.edu.biletflow.backend.repositories;

import jakarta.persistence.LockModeType;
import kz.edu.biletflow.backend.entities.OrganizerProfile;
import kz.edu.biletflow.backend.entities.OrganizerVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, Long> {
    boolean existsByUserId(Long userId);

    OrganizerProfile findByUserId(Long userId);

    Page<OrganizerProfile> findAllByVerificationStatus(OrganizerVerificationStatus status, Pageable pageable);

    // Row lock: a double-clicked save must not create two payout accounts.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM OrganizerProfile p WHERE p.user.id = :userId")
    Optional<OrganizerProfile> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM OrganizerProfile p WHERE p.id = :id")
    Optional<OrganizerProfile> findByIdForUpdate(@Param("id") Long id);
}
