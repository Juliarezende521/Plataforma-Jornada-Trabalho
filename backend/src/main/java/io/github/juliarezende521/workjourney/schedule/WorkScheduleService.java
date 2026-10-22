package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("!bootstrap")
class WorkScheduleService {
    private final CurrentEmployeeIdentity identity;private final EmployeeRepository employees;private final AppUserRepository users;
    private final WorkScheduleRepository schedules;private final AuditEntryRepository audits;private final Clock clock;
    WorkScheduleService(CurrentEmployeeIdentity identity,EmployeeRepository employees,AppUserRepository users,
                        WorkScheduleRepository schedules,AuditEntryRepository audits,Clock clock){
        this.identity=identity;this.employees=employees;this.users=users;this.schedules=schedules;this.audits=audits;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public View mine(LocalDate on){
        var actor=identity.require();var employee=employees.findOwnedActive(actor.employeeId(),actor.userId())
                .orElseThrow(()->new AccessDeniedException("Employee access denied"));
        return find(employee,on);
    }
    @Transactional(readOnly=true)
    public View forHr(UUID employeeId,LocalDate on){requireHr();return find(employees.findActiveById(employeeId)
            .orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible()),on);}
    @Transactional(timeout=15)
    public View create(UUID employeeId,LocalDate effectiveFrom,List<Integer> minutes){
        var actor=requireHr();var employee=employees.lockActive(employeeId)
                .orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible());
        if(schedules.existsByEmployee_IdAndEffectiveFrom(employeeId,effectiveFrom))throw new InvalidSchedule(409);
        var now=UtcInstants.normalize(clock.instant());
        var schedule=schedules.save(new WorkSchedule(employee,effectiveFrom,minutes,actor.userId(),now));
        audits.save(new AuditEntry(actor.userId(),"WORK_SCHEDULE_CREATED","WORK_SCHEDULE",schedule.id(),
                now,"effectiveFrom="+effectiveFrom));
        return View.from(schedule);
    }
    private CurrentEmployeeIdentity.Identity requireHr(){var actor=identity.require();
        if(!users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())
                ||employees.findOwnedActive(actor.employeeId(),actor.userId()).isEmpty())throw new AccessDeniedException("HR access denied");
        return actor;
    }
    private View find(Employee employee,LocalDate on){
        LocalDate date=on==null?UtcInstants.normalize(clock.instant()).atZone(ZoneId.of(employee.getReferenceTimeZoneId())).toLocalDate():on;
        return schedules.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employee.getId(),date)
                .map(View::from).orElse(null);
    }
    public record View(UUID id,UUID employeeId,LocalDate effectiveFrom,List<Integer> minutes,Instant createdAtUtc){
        static View from(WorkSchedule value){return new View(value.id(),value.employee().getId(),value.effectiveFrom(),value.minutes(),value.createdAt());}
    }
}
