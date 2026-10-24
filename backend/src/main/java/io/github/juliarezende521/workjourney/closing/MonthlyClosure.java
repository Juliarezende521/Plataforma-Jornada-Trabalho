package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "monthly_closure", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "competence_year", "competence_month"}))
class MonthlyClosure {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false) private Employee employee;
    @Column(name = "competence_year", nullable = false, updatable = false) private short competenceYear;
    @Column(name = "competence_month", nullable = false, updatable = false) private byte competenceMonth;
    @Column(name = "period_from", nullable = false, updatable = false) private LocalDate periodFrom;
    @Column(name = "period_to", nullable = false, updatable = false) private LocalDate periodTo;
    @Column(name = "total_work_seconds", nullable = false, updatable = false) private long totalWorkSeconds;
    @Column(name = "planned_work_seconds", nullable = false, updatable = false) private long plannedWorkSeconds;
    @Column(name = "balance_seconds", nullable = false, updatable = false) private long balanceSeconds;
    @Column(name = "tolerance_seconds", nullable = false, updatable = false) private long toleranceSeconds;
    @Column(name = "within_tolerance", nullable = false, updatable = false) private boolean withinTolerance;
    @Column(name = "total_sessions", nullable = false, updatable = false) private long totalSessions;
    @Column(name = "adjusted_sessions", nullable = false, updatable = false) private long adjustedSessions;
    @Column(name = "worked_days", nullable = false, updatable = false) private int workedDays;
    @Column(name = "closed_by_user_id", nullable = false, updatable = false) private UUID closedByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "closed_at_utc", nullable = false, updatable = false,
            columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime closedAtUtc;

    protected MonthlyClosure() { }

    MonthlyClosure(Employee employee, YearMonth competence, JourneyQueries.Summary summary,
                   UUID closedByUserId, Instant closedAt) {
        if (employee == null || competence == null || summary == null || closedByUserId == null || closedAt == null
                || summary.balanceSeconds() == null || summary.toleranceSeconds() == null
                || summary.withinTolerance() == null) {
            throw new InvalidMonthlyClosure();
        }
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.competenceYear = (short) competence.getYear();
        this.competenceMonth = (byte) competence.getMonthValue();
        this.periodFrom = competence.atDay(1);
        this.periodTo = competence.atEndOfMonth();
        this.totalWorkSeconds = Duration.parse(summary.totalWorkDuration()).getSeconds();
        this.plannedWorkSeconds = Duration.parse(summary.plannedWorkDuration()).getSeconds();
        this.balanceSeconds = summary.balanceSeconds();
        this.toleranceSeconds = summary.toleranceSeconds();
        this.withinTolerance = summary.withinTolerance();
        this.totalSessions = summary.totalSessions();
        this.adjustedSessions = summary.adjustedSessions();
        this.workedDays = summary.workedDays();
        this.closedByUserId = closedByUserId;
        this.closedAtUtc = UtcInstants.normalize(closedAt).atOffset(ZoneOffset.UTC);
    }

    UUID id() { return id; }
    UUID employeeId() { return employee.getId(); }
    YearMonth competence() { return YearMonth.of(competenceYear, competenceMonth); }
    LocalDate periodFrom() { return periodFrom; }
    LocalDate periodTo() { return periodTo; }
    long totalWorkSeconds() { return totalWorkSeconds; }
    long plannedWorkSeconds() { return plannedWorkSeconds; }
    long balanceSeconds() { return balanceSeconds; }
    long toleranceSeconds() { return toleranceSeconds; }
    boolean withinTolerance() { return withinTolerance; }
    long totalSessions() { return totalSessions; }
    long adjustedSessions() { return adjustedSessions; }
    int workedDays() { return workedDays; }
    Instant closedAt() { return closedAtUtc.toInstant(); }
}
