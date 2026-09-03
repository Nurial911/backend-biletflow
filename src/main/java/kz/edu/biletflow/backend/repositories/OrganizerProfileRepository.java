package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, Long> {
    boolean existsByUserId(Long userId);

    OrganizerProfile findByUserId(Long userId);
}
