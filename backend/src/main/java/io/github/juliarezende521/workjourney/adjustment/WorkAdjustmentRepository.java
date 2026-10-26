package io.github.juliarezende521.workjourney.adjustment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Profile("!bootstrap")
public interface WorkAdjustmentRepository extends JpaRepository<WorkAdjustmentRequest, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByEmployee_IdOrderByRequestedAtUtcDescIdDesc(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByEmployee_IdAndWorkSessionIdOrderByRequestedAtUtcDescIdDesc(
            UUID employeeId, UUID workSessionId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByStatusOrderByRequestedAtUtcAscIdAsc(AdjustmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    @Query("""
            SELECT request FROM WorkAdjustmentRequest request, TeamAssignment assignment
            WHERE request.employee.id = assignment.employee.id
              AND request.status = :status AND assignment.manager.id = :manager
              AND assignment.effectiveFrom = (
                SELECT MAX(current.effectiveFrom) FROM TeamAssignment current
                WHERE current.employee.id = request.employee.id AND current.effectiveFrom <= :on)
            ORDER BY request.requestedAtUtc ASC, request.id ASC
            """)
    Page<WorkAdjustmentRequest> findCurrentTeamPending(@Param("manager") UUID managerEmployeeId,
            @Param("on") java.time.LocalDate on, @Param("status") AdjustmentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM WorkAdjustmentRequest request JOIN FETCH request.employee WHERE request.id=:id")
    Optional<WorkAdjustmentRequest> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request FROM WorkAdjustmentRequest request JOIN FETCH request.employee,
                 TeamAssignment assignment
            WHERE request.id = :id AND request.employee.id = assignment.employee.id
              AND assignment.manager.id = :manager
              AND assignment.effectiveFrom = (
                SELECT MAX(current.effectiveFrom) FROM TeamAssignment current
                WHERE current.employee.id = request.employee.id AND current.effectiveFrom <= :on)
            """)
    Optional<WorkAdjustmentRequest> lockCurrentTeamRequest(@Param("id") UUID id,
            @Param("manager") UUID managerEmployeeId, @Param("on") java.time.LocalDate on);

    List<WorkAdjustmentRequest> findByWorkSessionIdAndStatusAndAppliedEventIdIsNullOrderByProposedOccurredAtUtcAscIdAsc(
            UUID workSessionId, AdjustmentStatus status);

    List<WorkAdjustmentRequest> findByWorkSessionIdAndStatusOrderByProposedOccurredAtUtcAscIdAsc(
            UUID workSessionId,AdjustmentStatus status);

    List<WorkAdjustmentRequest> findByWorkSessionIdInAndStatusOrderByWorkSessionIdAscProposedOccurredAtUtcAscIdAsc(
            List<UUID> workSessionIds, AdjustmentStatus status);

    @Query("""
            SELECT COUNT(request) FROM WorkAdjustmentRequest request, WorkSessionRow session
            WHERE request.workSessionId = session.id
              AND request.employee.id = :employee
              AND request.status = :status
              AND session.workDate BETWEEN :from AND :to
            """)
    long countInPeriod(@Param("employee") UUID employeeId, @Param("status") AdjustmentStatus status,
                       @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
