package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.StaffAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {
    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    Optional<StaffAssignment> findByEventIdAndUserId(Long eventId, Long userId);

    List<StaffAssignment> findAllByEventIdOrderByIdAsc(Long eventId);

    Page<StaffAssignment> findAllByUserId(Long userId, Pageable pageable);
}
