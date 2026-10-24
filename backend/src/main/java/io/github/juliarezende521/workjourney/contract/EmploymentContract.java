package io.github.juliarezende521.workjourney.contract;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "employment_contract", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "effective_from"}))
class EmploymentContract {
    enum Type { FULL_TIME, PART_TIME }

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "country_code", nullable = false, updatable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, updatable = false, length = 20)
    private Type type;

    @Column(name = "weekly_minutes", nullable = false, updatable = false)
    private short weeklyMinutes;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime createdAtUtc;

    protected EmploymentContract() { }

    EmploymentContract(Employee employee, LocalDate effectiveFrom, String countryCode, Type type,
                       int weeklyMinutes, UUID createdByUserId, Instant createdAt) {
        var normalizedCountry = countryCode == null ? null : countryCode.strip().toUpperCase(Locale.ROOT);
        if (employee == null || effectiveFrom == null || normalizedCountry == null
                || !COUNTRIES.contains(normalizedCountry) || type == null
                || weeklyMinutes < 1 || weeklyMinutes > 10080
                || createdByUserId == null || createdAt == null) {
            throw new InvalidContract();
        }
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.effectiveFrom = effectiveFrom;
        this.countryCode = normalizedCountry;
        this.type = type;
        this.weeklyMinutes = (short) weeklyMinutes;
        this.createdByUserId = createdByUserId;
        this.createdAtUtc = UtcInstants.normalize(createdAt).atOffset(ZoneOffset.UTC);
    }

    UUID id() { return id; }
    Employee employee() { return employee; }
    LocalDate effectiveFrom() { return effectiveFrom; }
    String countryCode() { return countryCode; }
    Type type() { return type; }
    int weeklyMinutes() { return weeklyMinutes; }
    Instant createdAt() { return createdAtUtc.toInstant(); }
}
