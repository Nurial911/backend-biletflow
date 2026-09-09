package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findAllByOrganizerId(Long organizerId, Pageable pageable);
}
