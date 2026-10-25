package io.github.juliarezende521.workjourney.team;

import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

@Profile("!bootstrap")
public interface TeamAssignmentRepository extends JpaRepository<TeamAssignment, UUID> {
    @EntityGraph(attributePaths = {"employee", "manager"})
    Optional<TeamAssignment> findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID employeeId, LocalDate on);

    boolean existsByEmployee_IdAndEffectiveFrom(UUID employeeId, LocalDate effectiveFrom);

    @EntityGraph(attributePaths = {"employee", "manager"})
    Page<TeamAssignment> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    @Query("""
            SELECT assignment FROM TeamAssignment assignment
            JOIN assignment.employee employee JOIN employee.user account
            WHERE assignment.manager.id = :manager AND account.active = true
              AND assignment.effectiveFrom = (
                SELECT MAX(current.effectiveFrom) FROM TeamAssignment current
                WHERE current.employee.id = employee.id AND current.effectiveFrom <= :on)
            """)
    Page<TeamAssignment> findCurrentTeam(@Param("manager") UUID managerEmployeeId,
                                         @Param("on") LocalDate on, Pageable pageable);

    @Query("""
            SELECT COUNT(assignment) FROM TeamAssignment assignment
            WHERE assignment.manager.id = :manager
              AND assignment.effectiveFrom = (
                SELECT MAX(current.effectiveFrom) FROM TeamAssignment current
                WHERE current.employee.id = assignment.employee.id AND current.effectiveFrom <= :on)
            """)
    long countCurrentTeam(@Param("manager") UUID managerEmployeeId, @Param("on") LocalDate on);
}
