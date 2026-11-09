package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {
}
