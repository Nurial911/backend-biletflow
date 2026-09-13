package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
