package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {
}
