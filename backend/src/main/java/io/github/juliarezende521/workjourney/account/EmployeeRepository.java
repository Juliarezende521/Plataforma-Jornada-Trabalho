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
    @Query("SELECT e FROM Employee e JOIN e.user u WHERE u.active = true")
    org.springframework.data.domain.Page<Employee> findActive(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT e FROM Employee e JOIN e.user u
            WHERE u.active = true AND e.name LIKE CONCAT(:prefix, '%') ESCAPE '~'
            """)
    org.springframework.data.domain.Page<Employee> findActiveByNamePrefix(
            @Param("prefix") String prefix, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT e FROM Employee e JOIN e.user u WHERE e.id = :id AND u.active = true")
    Optional<Employee> findActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e JOIN e.user u WHERE e.id = :id AND u.active = true")
    Optional<Employee> lockActive(@Param("id") UUID id);

    String OWNED_ACTIVE_QUERY = "SELECT e FROM Employee e JOIN e.user u WHERE e.id = :employee AND u.id = :user AND u.active = true";

    @Query(OWNED_ACTIVE_QUERY)
    Optional<Employee> findOwnedActive(@Param("employee") UUID employeeId, @Param("user") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(OWNED_ACTIVE_QUERY)
    Optional<Employee> lockOwnedActive(@Param("employee") UUID employeeId, @Param("user") UUID userId);
}
