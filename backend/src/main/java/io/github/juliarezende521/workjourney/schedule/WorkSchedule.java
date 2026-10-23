package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name="work_schedule",schema="dbo",uniqueConstraints=@UniqueConstraint(columnNames={"employee_id","effective_from"}))
class WorkSchedule {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false,updatable=false) private Employee employee;
    @Column(name="effective_from",nullable=false,updatable=false) private LocalDate effectiveFrom;
    @Column(name="monday_minutes",nullable=false,updatable=false) private short mondayMinutes;
    @Column(name="tuesday_minutes",nullable=false,updatable=false) private short tuesdayMinutes;
    @Column(name="wednesday_minutes",nullable=false,updatable=false) private short wednesdayMinutes;
    @Column(name="thursday_minutes",nullable=false,updatable=false) private short thursdayMinutes;
    @Column(name="friday_minutes",nullable=false,updatable=false) private short fridayMinutes;
    @Column(name="saturday_minutes",nullable=false,updatable=false) private short saturdayMinutes;
    @Column(name="sunday_minutes",nullable=false,updatable=false) private short sundayMinutes;
    @Column(name="tolerance_minutes",nullable=false,updatable=false) private short toleranceMinutes;
    @Column(name="created_by_user_id",nullable=false,updatable=false) private UUID createdByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="created_at_utc",nullable=false,updatable=false,columnDefinition="datetimeoffset(7)",precision=7)
    private OffsetDateTime createdAtUtc;
    protected WorkSchedule(){}
    WorkSchedule(Employee employee,LocalDate effectiveFrom,List<Integer> minutes,int toleranceMinutes,UUID creator,Instant createdAt){
        if(employee==null||effectiveFrom==null||creator==null||createdAt==null||minutes==null||minutes.size()!=7
                ||minutes.stream().anyMatch(value->value==null||value<0||value>1440)
                ||minutes.stream().mapToInt(Integer::intValue).sum()==0||toleranceMinutes<0||toleranceMinutes>120)throw new InvalidSchedule();
        id=UUID.randomUUID();this.employee=employee;this.effectiveFrom=effectiveFrom;createdByUserId=creator;
        createdAtUtc=UtcInstants.normalize(createdAt).atOffset(ZoneOffset.UTC);
        mondayMinutes=minutes.get(0).shortValue();tuesdayMinutes=minutes.get(1).shortValue();wednesdayMinutes=minutes.get(2).shortValue();
        thursdayMinutes=minutes.get(3).shortValue();fridayMinutes=minutes.get(4).shortValue();saturdayMinutes=minutes.get(5).shortValue();sundayMinutes=minutes.get(6).shortValue();
        this.toleranceMinutes=(short)toleranceMinutes;
    }
    UUID id(){return id;} Employee employee(){return employee;} LocalDate effectiveFrom(){return effectiveFrom;}
    Instant createdAt(){return createdAtUtc.toInstant();}
    List<Integer> minutes(){return List.of((int)mondayMinutes,(int)tuesdayMinutes,(int)wednesdayMinutes,(int)thursdayMinutes,
            (int)fridayMinutes,(int)saturdayMinutes,(int)sundayMinutes);}
    int toleranceMinutes(){return toleranceMinutes;}
}
