package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    long countBySuspendedTrue();

    // Read by JwtAuthenticationFilter on every authenticated request. Empty for a deleted account.
    @Query("SELECT u.suspended FROM User u WHERE u.id = :id")
    Optional<Boolean> findSuspendedById(@Param("id") Long id);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("SELECT u.id FROM User u WHERE u.role = :role")
    List<Long> findIdsByRole(@Param("role") User.Role role);
}
