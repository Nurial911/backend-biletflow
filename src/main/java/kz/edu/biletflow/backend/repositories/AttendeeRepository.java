package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Attendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendeeRepository extends JpaRepository<Attendee, Long> {
    Optional<Attendee> findByUserId(Long userId);
}
