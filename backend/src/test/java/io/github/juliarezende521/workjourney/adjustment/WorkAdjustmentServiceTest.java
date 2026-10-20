package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.AppUser;
import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.WorkSession;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkAdjustmentServiceTest {
    private final CurrentEmployeeIdentity identity=mock(CurrentEmployeeIdentity.class);
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final JpaWorkSessionStore sessions=mock(JpaWorkSessionStore.class);
    private final WorkAdjustmentRepository requests=mock(WorkAdjustmentRepository.class);
    private final AuditEntryRepository audits=mock(AuditEntryRepository.class);
    private final Instant now=Instant.parse("2026-10-20T12:00:00Z");
    private final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    private final UUID userId=UUID.randomUUID();
    private final Employee employee=new Employee(new AppUser("worker@example.invalid", "$2a$12$"+"A".repeat(53)), "Worker", "America/Sao_Paulo");
    private WorkAdjustmentService service;

    @BeforeEach void setUp() {
        service=new WorkAdjustmentService(identity,employees,sessions,requests,audits,new EffectiveTimelineService(requests),clock);
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(userId,employee.getId()));
        when(employees.lockOwnedActive(employee.getId(),userId)).thenReturn(Optional.of(employee));
        when(employees.lockActive(employee.getId())).thenReturn(Optional.of(employee));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requests.findByWorkSessionIdAndStatusOrderByProposedOccurredAtUtcAscIdAsc(any(),eq(AdjustmentStatus.APPROVED)))
                .thenReturn(java.util.List.of());
    }

    @Test void createsOwnedPendingRequestAndAuditWithoutChangingOriginalSession() {
        var original=WorkSession.open(employee.getId(),userId,"America/Sao_Paulo",now.minus(Duration.ofHours(8)));
        when(sessions.findOwned(original.getId(),employee.getId())).thenReturn(Optional.of(new JpaWorkSessionStore.StoredSession(original,0)));
        var result=service.request(original.getId(),WorkEventType.PAUSE_START,now.minus(Duration.ofHours(4)),"Esqueci de registrar a pausa.");
        assertEquals(AdjustmentStatus.PENDING,result.status());
        assertEquals(WorkEventType.PAUSE_START,result.requestedEventType());
        assertEquals(1,original.getEvents().size(),"request must preserve original events");
        verify(audits).save(any(AuditEntry.class));
    }

    @Test void rejectsFutureTimestampBeforePersistence() {
        var original=WorkSession.open(employee.getId(),userId,"America/Sao_Paulo",now.minus(Duration.ofHours(1)));
        when(sessions.findOwned(original.getId(),employee.getId())).thenReturn(Optional.of(new JpaWorkSessionStore.StoredSession(original,0)));
        assertThrows(InvalidAdjustment.class,() -> service.request(original.getId(),WorkEventType.EXIT,now.plusSeconds(1),"Horário informado para saída."));
        verifyNoInteractions(audits); verify(requests,never()).save(any());
    }

    @Test void locksAndAuditsAnIrreversibleDecision() {
        var original=WorkSession.open(employee.getId(),userId,"America/Sao_Paulo",now.minus(Duration.ofHours(8)));
        original.record(WorkEventType.EXIT,now.minusSeconds(30));
        var request=new WorkAdjustmentRequest(employee,userId,original.getId(),WorkEventType.PAUSE_START,now.minus(Duration.ofHours(4)),"Pausa não registrada no horário.",now.minusSeconds(20));
        when(requests.lockById(request.getId())).thenReturn(Optional.of(request));
        when(sessions.findOwned(original.getId(),employee.getId())).thenReturn(Optional.of(new JpaWorkSessionStore.StoredSession(original,0)));
        var result=service.decide(request.getId(),AdjustmentStatus.APPROVED,"Conferido com a liderança.");
        assertEquals(AdjustmentStatus.APPROVED,result.status());
        assertThrows(AdjustmentConflict.class,() -> service.decide(request.getId(),AdjustmentStatus.REJECTED,null));
        verify(requests,times(2)).lockById(request.getId());
        verify(audits,times(1)).save(any(AuditEntry.class));
    }

    @Test void rejectsApprovalThatWouldCreateAnInvalidTimeline() {
        var original=WorkSession.open(employee.getId(),userId,"America/Sao_Paulo",now.minus(Duration.ofHours(8)));
        var request=new WorkAdjustmentRequest(employee,userId,original.getId(),WorkEventType.ENTRY,now.minusSeconds(60),"Outra entrada foi informada por engano.",now.minusSeconds(30));
        when(requests.lockById(request.getId())).thenReturn(Optional.of(request));
        when(sessions.findOwned(original.getId(),employee.getId())).thenReturn(Optional.of(new JpaWorkSessionStore.StoredSession(original,0)));
        assertThrows(AdjustmentConflict.class,() -> service.decide(request.getId(),AdjustmentStatus.APPROVED,null));
        assertEquals(AdjustmentStatus.PENDING,request.getStatus());
        verifyNoInteractions(audits);
    }
}
