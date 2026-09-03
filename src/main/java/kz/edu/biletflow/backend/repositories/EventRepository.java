package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Long, Event> {
}
