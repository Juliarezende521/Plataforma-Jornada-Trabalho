package io.github.juliarezende521.workjourney.policy;

import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "country_work_policy", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "effective_from"}))
class CountryWorkPolicy {
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    @Id private UUID id;
    @Column(name = "country_code", nullable = false, updatable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;
    @Column(name = "effective_from", nullable = false, updatable = false) private LocalDate effectiveFrom;
    @Column(name = "standard_weekly_minutes", nullable = false, updatable = false) private short standardWeeklyMinutes;
    @Column(name = "maximum_daily_minutes", nullable = false, updatable = false) private short maximumDailyMinutes;
    @Column(name = "minimum_break_minutes", nullable = false, updatable = false) private short minimumBreakMinutes;
    @Column(name = "overtime_tolerance_minutes", nullable = false, updatable = false) private short overtimeToleranceMinutes;
    @Column(name = "created_by_user_id", nullable = false, updatable = false) private UUID createdByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime createdAtUtc;

    protected CountryWorkPolicy() { }

    CountryWorkPolicy(String countryCode, LocalDate effectiveFrom, int standardWeeklyMinutes,
                      int maximumDailyMinutes, int minimumBreakMinutes, int overtimeToleranceMinutes,
                      UUID createdByUserId, Instant createdAt) {
        var country = normalize(countryCode);
        if (effectiveFrom == null || standardWeeklyMinutes < 1 || standardWeeklyMinutes > 10080
                || maximumDailyMinutes < 1 || maximumDailyMinutes > 1440
                || minimumBreakMinutes < 0 || minimumBreakMinutes > 1440
                || overtimeToleranceMinutes < 0 || overtimeToleranceMinutes > 1440
                || createdByUserId == null || createdAt == null) throw new InvalidCountryWorkPolicy();
        this.id = UUID.randomUUID(); this.countryCode = country; this.effectiveFrom = effectiveFrom;
        this.standardWeeklyMinutes = (short) standardWeeklyMinutes;
        this.maximumDailyMinutes = (short) maximumDailyMinutes;
        this.minimumBreakMinutes = (short) minimumBreakMinutes;
        this.overtimeToleranceMinutes = (short) overtimeToleranceMinutes;
        this.createdByUserId = createdByUserId;
        this.createdAtUtc = UtcInstants.normalize(createdAt).atOffset(ZoneOffset.UTC);
    }

    static String normalize(String value) {
        var country = value == null ? null : value.strip().toUpperCase(Locale.ROOT);
        if (country == null || !COUNTRIES.contains(country)) throw new InvalidCountryWorkPolicy();
        return country;
    }
    UUID id() { return id; }
    String countryCode() { return countryCode; }
    LocalDate effectiveFrom() { return effectiveFrom; }
    int standardWeeklyMinutes() { return standardWeeklyMinutes; }
    int maximumDailyMinutes() { return maximumDailyMinutes; }
    int minimumBreakMinutes() { return minimumBreakMinutes; }
    int overtimeToleranceMinutes() { return overtimeToleranceMinutes; }
    Instant createdAt() { return createdAtUtc.toInstant(); }
}
