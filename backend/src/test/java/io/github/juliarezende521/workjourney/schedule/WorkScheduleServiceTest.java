package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkScheduleServiceTest {
    @Test void hrCreatesAnImmutableAuditedScheduleAndDuplicateDateConflicts(){
        var identity=mock(CurrentEmployeeIdentity.class);var employees=mock(EmployeeRepository.class);var users=mock(AppUserRepository.class);
        var schedules=mock(WorkScheduleRepository.class);var exceptions=mock(WorkScheduleExceptionRepository.class);var audits=mock(AuditEntryRepository.class);
        var account=new AppUser("hr@example.invalid","$2a$12$"+"A".repeat(53));var hr=new Employee(account,"RH","UTC");
        var target=new Employee(new AppUser("worker@example.invalid","$2a$12$"+"B".repeat(53)),"Worker","Europe/Lisbon");
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(account.getId(),hr.getId()));
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(account.getId())).thenReturn(true);
        when(employees.findOwnedActive(hr.getId(),account.getId())).thenReturn(Optional.of(hr));when(employees.lockActive(target.getId())).thenReturn(Optional.of(target));
        when(schedules.save(any())).thenAnswer(call->call.getArgument(0));
        var service=new WorkScheduleService(identity,employees,users,schedules,exceptions,audits,Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"),ZoneOffset.UTC));
        var date=LocalDate.of(2026,11,1);var minutes=List.of(480,480,480,480,480,0,0);
        var result=service.create(target.getId(),date,minutes,10);assertEquals(minutes,result.minutes());assertEquals(10,result.toleranceMinutes());
        when(exceptions.save(any())).thenAnswer(call->call.getArgument(0));
        var exception=service.createException(target.getId(),date,0,0,WorkScheduleException.Category.HOLIDAY,"Feriado local");
        assertEquals("HOLIDAY",exception.category());verify(audits,times(2)).save(any(AuditEntry.class));
        when(schedules.existsByEmployee_IdAndEffectiveFrom(target.getId(),date)).thenReturn(true);
        assertEquals(409,assertThrows(InvalidSchedule.class,()->service.create(target.getId(),date,minutes,10)).status());
    }
    @Test void hrHistoryIsBoundedAndSortedWithoutExposingInactiveEmployees(){
        var identity=mock(CurrentEmployeeIdentity.class);var employees=mock(EmployeeRepository.class);var users=mock(AppUserRepository.class);
        var schedules=mock(WorkScheduleRepository.class);var exceptions=mock(WorkScheduleExceptionRepository.class);var audits=mock(AuditEntryRepository.class);
        var account=new AppUser("hr@example.invalid","$2a$12$"+"A".repeat(53));var hr=new Employee(account,"RH","UTC");
        var target=new Employee(new AppUser("worker@example.invalid","$2a$12$"+"B".repeat(53)),"Worker","UTC");
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(account.getId(),hr.getId()));
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(account.getId())).thenReturn(true);
        when(employees.findOwnedActive(hr.getId(),account.getId())).thenReturn(Optional.of(hr));
        when(employees.findActiveById(target.getId())).thenReturn(Optional.of(target));
        when(schedules.findByEmployee_Id(eq(target.getId()),any())).thenReturn(org.springframework.data.domain.Page.empty());
        var service=new WorkScheduleService(identity,employees,users,schedules,exceptions,audits,Clock.systemUTC());
        assertEquals(0,service.historyForHr(target.getId(),0,20).totalElements());
        var pageable=org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);verify(schedules).findByEmployee_Id(eq(target.getId()),pageable.capture());
        assertEquals("effectiveFrom: DESC,id: DESC",pageable.getValue().getSort().toString());
        assertThrows(InvalidSchedule.class,()->service.historyForHr(target.getId(),0,101));
    }
}
