package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.TicketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    Page<TicketType> findAllByEventId(Long eventId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    java.util.Optional<TicketType> findByIdForUpdate(@Param("id") Long id);
}
