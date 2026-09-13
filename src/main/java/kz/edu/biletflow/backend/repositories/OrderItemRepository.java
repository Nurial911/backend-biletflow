package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
