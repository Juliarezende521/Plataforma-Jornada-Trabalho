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
import org.springframework.data.domain.*;

@Service @Profile("!bootstrap")
class WorkScheduleService {
    private final CurrentEmployeeIdentity identity;private final EmployeeRepository employees;private final AppUserRepository users;
    private final WorkScheduleRepository schedules;private final AuditEntryRepository audits;private final Clock clock;
    private final WorkScheduleExceptionRepository exceptions;
    WorkScheduleService(CurrentEmployeeIdentity identity,EmployeeRepository employees,AppUserRepository users,
                        WorkScheduleRepository schedules,WorkScheduleExceptionRepository exceptions,AuditEntryRepository audits,Clock clock){
        this.identity=identity;this.employees=employees;this.users=users;this.schedules=schedules;this.exceptions=exceptions;this.audits=audits;this.clock=clock;
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
    @Transactional(readOnly=true)
    public Page historyForHr(UUID employeeId,int page,int size){requireHr();
        if(page<0||page>Integer.MAX_VALUE/100||size<1||size>100)throw new InvalidSchedule();
        employees.findActiveById(employeeId).orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible());
        var result=schedules.findByEmployee_Id(employeeId,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"effectiveFrom","id")));
        return new Page(result.map(View::from).getContent(),page,size,result.getTotalElements());
    }
    @Transactional(readOnly=true)
    public ExceptionPage exceptionHistoryForHr(UUID employeeId,int page,int size){requireHr();validatePage(page,size);
        employees.findActiveById(employeeId).orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible());
        var result=exceptions.findByEmployee_Id(employeeId,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"workDate","createdAtUtc","id")));
        return new ExceptionPage(result.map(ExceptionView::from).getContent(),page,size,result.getTotalElements());
    }
    @Transactional(timeout=15)
    public ExceptionView createException(UUID employeeId,LocalDate date,int planned,int tolerance,WorkScheduleException.Category category,String reason){
        var actor=requireHr();var employee=employees.lockActive(employeeId)
                .orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible());
        var now=UtcInstants.normalize(clock.instant());var value=exceptions.save(new WorkScheduleException(employee,date,planned,tolerance,category,reason,actor.userId(),now));
        audits.save(new AuditEntry(actor.userId(),"WORK_SCHEDULE_EXCEPTION_CREATED","WORK_SCHEDULE_EXCEPTION",value.id(),now,
                "workDate="+date+",category="+category+",plannedMinutes="+planned+",toleranceMinutes="+tolerance));
        return ExceptionView.from(value);
    }
    @Transactional(timeout=15)
    public View create(UUID employeeId,LocalDate effectiveFrom,List<Integer> minutes,int toleranceMinutes){
        var actor=requireHr();var employee=employees.lockActive(employeeId)
                .orElseThrow(()->new io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible());
        if(schedules.existsByEmployee_IdAndEffectiveFrom(employeeId,effectiveFrom))throw new InvalidSchedule(409);
        var now=UtcInstants.normalize(clock.instant());
        var schedule=schedules.save(new WorkSchedule(employee,effectiveFrom,minutes,toleranceMinutes,actor.userId(),now));
        audits.save(new AuditEntry(actor.userId(),"WORK_SCHEDULE_CREATED","WORK_SCHEDULE",schedule.id(),
                now,"effectiveFrom="+effectiveFrom+",toleranceMinutes="+toleranceMinutes));
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
    public record View(UUID id,UUID employeeId,LocalDate effectiveFrom,List<Integer> minutes,int toleranceMinutes,Instant createdAtUtc){
        static View from(WorkSchedule value){return new View(value.id(),value.employee().getId(),value.effectiveFrom(),value.minutes(),value.toleranceMinutes(),value.createdAt());}
    }
    public record Page(List<View> items,int page,int size,long totalElements){}
    private void validatePage(int page,int size){if(page<0||page>Integer.MAX_VALUE/100||size<1||size>100)throw new InvalidSchedule();}
    public record ExceptionView(UUID id,UUID employeeId,LocalDate workDate,int plannedMinutes,int toleranceMinutes,String category,String reason,Instant createdAtUtc){
        static ExceptionView from(WorkScheduleException value){return new ExceptionView(value.id(),value.employee().getId(),value.workDate(),value.plannedMinutes(),value.toleranceMinutes(),value.category().name(),value.reason(),value.createdAt());}
    }
    public record ExceptionPage(List<ExceptionView> items,int page,int size,long totalElements){}
}
