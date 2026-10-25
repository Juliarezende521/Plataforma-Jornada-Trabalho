package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "team_assignment", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "effective_from"}))
class TeamAssignment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false) private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_employee_id", nullable = false, updatable = false) private Employee manager;
    @Column(name = "effective_from", nullable = false, updatable = false) private LocalDate effectiveFrom;
    @Column(name = "created_by_user_id", nullable = false, updatable = false) private UUID createdByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime createdAtUtc;

    protected TeamAssignment() { }

    TeamAssignment(Employee employee, Employee manager, LocalDate effectiveFrom,
                   UUID createdByUserId, Instant createdAt) {
        if (employee == null || manager == null || employee.getId().equals(manager.getId())
                || effectiveFrom == null || createdByUserId == null || createdAt == null) {
            throw new InvalidTeamAssignment();
        }
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.manager = manager;
        this.effectiveFrom = effectiveFrom;
        this.createdByUserId = createdByUserId;
        this.createdAtUtc = UtcInstants.normalize(createdAt).atOffset(ZoneOffset.UTC);
    }

    UUID id() { return id; }
    Employee employee() { return employee; }
    Employee manager() { return manager; }
    LocalDate effectiveFrom() { return effectiveFrom; }
    Instant createdAt() { return createdAtUtc.toInstant(); }
}
