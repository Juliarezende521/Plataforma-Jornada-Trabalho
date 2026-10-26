package io.github.juliarezende521.workjourney.closing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Profile("!bootstrap")
interface MonthlyClosureRepository extends JpaRepository<MonthlyClosure, UUID> {
    Optional<MonthlyClosure> findByEmployee_IdAndCompetenceYearAndCompetenceMonth(
            UUID employeeId, short year, byte month);
    boolean existsByEmployee_IdAndCompetenceYearAndCompetenceMonth(UUID employeeId, short year, byte month);
    boolean existsByEmployee_IdAndPeriodToGreaterThanEqual(UUID employeeId, java.time.LocalDate date);
    Page<MonthlyClosure> findByEmployee_Id(UUID employeeId, Pageable pageable);

    /** Scalar projection keeps the consolidated export to one bounded database query. */
    @Query("""
            SELECT e.name AS employeeName, e.referenceTimeZoneId AS referenceTimeZoneId,
                   c.totalWorkSeconds AS totalWorkSeconds, c.plannedWorkSeconds AS plannedWorkSeconds,
                   c.balanceSeconds AS balanceSeconds, c.toleranceSeconds AS toleranceSeconds,
                   c.withinTolerance AS withinTolerance, c.totalSessions AS totalSessions,
                   c.adjustedSessions AS adjustedSessions, c.workedDays AS workedDays,
                   c.closedAtUtc AS closedAtUtc
            FROM MonthlyClosure c JOIN c.employee e
            WHERE c.competenceYear = :year AND c.competenceMonth = :month
            ORDER BY e.name, e.id
            """)
    java.util.List<ConsolidatedRow> consolidated(@Param("year") short year, @Param("month") byte month,
                                                  Pageable limit);

    interface ConsolidatedRow {
        String getEmployeeName();
        String getReferenceTimeZoneId();
        long getTotalWorkSeconds();
        long getPlannedWorkSeconds();
        long getBalanceSeconds();
        long getToleranceSeconds();
        boolean getWithinTolerance();
        long getTotalSessions();
        long getAdjustedSessions();
        int getWorkedDays();
        java.time.OffsetDateTime getClosedAtUtc();
    }
}
