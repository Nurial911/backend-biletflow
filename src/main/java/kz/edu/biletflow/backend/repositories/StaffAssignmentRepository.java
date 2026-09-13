package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {
    boolean existsByEventIdAndEventAdminId(Long eventId, Long eventAdminId);
}
