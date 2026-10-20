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
interface WorkAdjustmentRepository extends JpaRepository<WorkAdjustmentRequest, UUID> {
    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByEmployee_IdOrderByRequestedAtUtcDescIdDesc(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByEmployee_IdAndWorkSessionIdOrderByRequestedAtUtcDescIdDesc(
            UUID employeeId, UUID workSessionId, Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    Page<WorkAdjustmentRequest> findByStatusOrderByRequestedAtUtcAscIdAsc(AdjustmentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM WorkAdjustmentRequest request JOIN FETCH request.employee WHERE request.id=:id")
    Optional<WorkAdjustmentRequest> lockById(@Param("id") UUID id);

    List<WorkAdjustmentRequest> findByWorkSessionIdAndStatusOrderByProposedOccurredAtUtcAscIdAsc(
            UUID workSessionId, AdjustmentStatus status);
}
