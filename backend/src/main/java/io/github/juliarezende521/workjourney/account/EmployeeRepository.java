package io.github.juliarezende521.workjourney.account;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

@Profile("!bootstrap")
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    String OWNED_ACTIVE_QUERY = "SELECT e FROM Employee e JOIN e.user u WHERE e.id = :employee AND u.id = :user AND u.active = true";

    @Query(OWNED_ACTIVE_QUERY)
    Optional<Employee> findOwnedActive(@Param("employee") UUID employeeId, @Param("user") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(OWNED_ACTIVE_QUERY)
    Optional<Employee> lockOwnedActive(@Param("employee") UUID employeeId, @Param("user") UUID userId);
}
