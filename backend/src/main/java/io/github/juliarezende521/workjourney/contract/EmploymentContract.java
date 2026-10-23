package io.github.juliarezende521.workjourney.contract;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name="employment_contract",schema="dbo",uniqueConstraints=@UniqueConstraint(columnNames={"employee_id","effective_from"}))
class EmploymentContract {
    enum Type { FULL_TIME, PART_TIME }
    private static final Set<String> COUNTRIES=Set.of(Locale.getISOCountries());
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false,updatable=false) private Employee employee;
    @Column(name="effective_from",nullable=false,updatable=false) private LocalDate effectiveFrom;
    @Column(name="country_code",nullable=false,updatable=false,length=2,columnDefinition="char(2)") private String countryCode;
    @Enumerated(EnumType.STRING) @Column(name="employment_type",nullable=false,updatable=false,length=20) private Type type;
    @Column(name="weekly_minutes",nullable=false,updatable=false) private short weeklyMinutes;
    @Column(name="created_by_user_id",nullable=false,updatable=false) private UUID createdByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="created_at_utc",nullable=false,updatable=false,columnDefinition="datetimeoffset(7)",precision=7) private OffsetDateTime createdAtUtc;
    protected EmploymentContract(){}
    EmploymentContract(Employee employee,LocalDate effectiveFrom,String country,Type type,int weeklyMinutes,UUID creator,Instant created){
        var normalized=country==null?null:country.strip().toUpperCase(Locale.ROOT);
        if(employee==null||effectiveFrom==null||normalized==null||!COUNTRIES.contains(normalized)||type==null
                ||weeklyMinutes<1||weeklyMinutes>10080||creator==null||created==null)throw new InvalidContract();
        id=UUID.randomUUID();this.employee=employee;this.effectiveFrom=effectiveFrom;countryCode=normalized;this.type=type;
        this.weeklyMinutes=(short)weeklyMinutes;createdByUserId=creator;createdAtUtc=UtcInstants.normalize(created).atOffset(ZoneOffset.UTC);
    }
    UUID id(){return id;} Employee employee(){return employee;} LocalDate effectiveFrom(){return effectiveFrom;}
    String countryCode(){return countryCode;} Type type(){return type;} int weeklyMinutes(){return weeklyMinutes;}
    Instant createdAt(){return createdAtUtc.toInstant();}
}
