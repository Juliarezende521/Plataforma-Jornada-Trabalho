package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name="work_schedule_exception",schema="dbo")
class WorkScheduleException {
    enum Category { HOLIDAY,TIME_OFF,TRAINING,SPECIAL }
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false,updatable=false) private Employee employee;
    @Column(name="work_date",nullable=false,updatable=false) private LocalDate workDate;
    @Column(name="planned_minutes",nullable=false,updatable=false) private short plannedMinutes;
    @Column(name="tolerance_minutes",nullable=false,updatable=false) private short toleranceMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=24) private Category category;
    @Column(nullable=false,updatable=false,length=200) private String reason;
    @Column(name="created_by_user_id",nullable=false,updatable=false) private UUID createdByUserId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="created_at_utc",nullable=false,updatable=false,columnDefinition="datetimeoffset(7)",precision=7)
    private OffsetDateTime createdAtUtc;
    protected WorkScheduleException(){}
    WorkScheduleException(Employee employee,LocalDate date,int planned,int tolerance,Category category,String reason,UUID creator,Instant created){
        var text=reason==null?null:reason.trim();
        if(employee==null||date==null||planned<0||planned>1440||tolerance<0||tolerance>120||category==null
                ||text==null||text.length()<3||text.length()>200||creator==null||created==null)throw new InvalidSchedule();
        id=UUID.randomUUID();this.employee=employee;workDate=date;plannedMinutes=(short)planned;toleranceMinutes=(short)tolerance;
        this.category=category;this.reason=text;createdByUserId=creator;createdAtUtc=UtcInstants.normalize(created).atOffset(ZoneOffset.UTC);
    }
    UUID id(){return id;} LocalDate workDate(){return workDate;} int plannedMinutes(){return plannedMinutes;}
    int toleranceMinutes(){return toleranceMinutes;} Category category(){return category;} String reason(){return reason;}
    Employee employee(){return employee;} Instant createdAt(){return createdAtUtc.toInstant();}
}
