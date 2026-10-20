package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class WorkAdjustmentService {
    private static final int MAX_PAGE_SIZE = 50;
    private final CurrentEmployeeIdentity identity;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore sessions;
    private final WorkAdjustmentRepository requests;
    private final AuditEntryRepository audits;
    private final EffectiveTimelineService timelines;
    private final Clock clock;

    public WorkAdjustmentService(CurrentEmployeeIdentity identity, EmployeeRepository employees,
                                 JpaWorkSessionStore sessions, WorkAdjustmentRepository requests,
                                 AuditEntryRepository audits, EffectiveTimelineService timelines, Clock clock) {
        this.identity=identity; this.employees=employees; this.sessions=sessions;
        this.requests=requests; this.audits=audits; this.timelines=timelines; this.clock=clock;
    }

    @Transactional(timeout = 15)
    public View request(UUID sessionId, WorkEventType type, Instant proposedAt, String reason) {
        var actor=identity.require();
        var employee=employees.lockOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        var session=sessions.findOwned(sessionId, employee.getId()).orElseThrow(AdjustmentNotVisible::new).session();
        Instant now=now();
        Instant proposed=UtcInstants.normalize(proposedAt);
        String validReason=text(reason, 10, 500, true);
        if (type==null || proposed.isAfter(now)
                || proposed.isBefore(session.getOpenedAtUtc().minus(Duration.ofHours(24)))
                || proposed.isAfter((session.getClosedAtUtc()==null ? now : session.getClosedAtUtc()).plus(Duration.ofHours(24)))) {
            throw new InvalidAdjustment();
        }
        var request=requests.save(new WorkAdjustmentRequest(employee, actor.userId(), sessionId, type, proposed, validReason, now));
        audits.save(new AuditEntry(actor.userId(), "ADJUSTMENT_REQUESTED", request.getId(), now, null));
        return View.from(request);
    }

    @Transactional(readOnly = true)
    public Page mine(UUID sessionId, int page, int size) {
        validatePage(page,size);
        var actor=identity.require();
        employees.findOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        var pageable=PageRequest.of(page,size);
        var result=sessionId==null
                ? requests.findByEmployee_IdOrderByRequestedAtUtcDescIdDesc(actor.employeeId(), pageable)
                : requests.findByEmployee_IdAndWorkSessionIdOrderByRequestedAtUtcDescIdDesc(actor.employeeId(), sessionId, pageable);
        return new Page(result.getContent().stream().map(View::from).toList(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page pending(int page, int size) {
        validatePage(page,size);
        identity.require();
        var result=requests.findByStatusOrderByRequestedAtUtcAscIdAsc(AdjustmentStatus.PENDING, PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(View::from).toList(), page, size, result.getTotalElements());
    }

    @Transactional(timeout = 15)
    public View decide(UUID id, AdjustmentStatus decision, String note) {
        var actor=identity.require();
        String validNote=text(note, 0, 500, false);
        Instant now=now();
        var request=requests.lockById(id).orElseThrow(AdjustmentNotVisible::new);
        var employee=employees.lockActive(request.getEmployee().getId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        if (decision==AdjustmentStatus.APPROVED) {
            var session=sessions.findOwned(request.getWorkSessionId(),employee.getId()).orElseThrow(AdjustmentNotVisible::new).session();
            var effective=timelines.projectIncluding(session,request,now);
            // Online checkpoints still use originals. Until correction-aware commands exist,
            // an approval may change duration but not the persisted open/closed state.
            if(effective.state()!=session.getState())throw new AdjustmentConflict();
        }
        request.decide(decision, actor.userId(), now, validNote);
        audits.save(new AuditEntry(actor.userId(), decision==AdjustmentStatus.APPROVED
                ? "ADJUSTMENT_APPROVED" : "ADJUSTMENT_REJECTED", request.getId(), now, validNote));
        return View.from(request);
    }

    private static String text(String value, int min, int max, boolean required) {
        if (value==null) {
            if (required) throw new InvalidAdjustment();
            return null;
        }
        String normalized=value.strip();
        if (normalized.length()<min || normalized.length()>max) throw new InvalidAdjustment();
        return normalized.isEmpty() ? null : normalized;
    }
    private static void validatePage(int page,int size) {
        if (page<0 || size<1 || size>MAX_PAGE_SIZE) throw new InvalidAdjustment();
    }
    private Instant now() { return UtcInstants.normalize(clock.instant()); }

    public record View(UUID id, UUID workSessionId, UUID employeeId, String employeeName,
                       WorkEventType requestedEventType, Instant proposedOccurredAtUtc,
                       String reason, AdjustmentStatus status, Instant requestedAtUtc,
                       Instant decidedAtUtc, String decisionNote) {
        static View from(WorkAdjustmentRequest request) {
            return new View(request.getId(), request.getWorkSessionId(), request.getEmployee().getId(),
                    request.getEmployee().getName(), request.getRequestedEventType(), request.getProposedOccurredAtUtc(),
                    request.getReason(), request.getStatus(), request.getRequestedAtUtc(),
                    request.getDecidedAtUtc(), request.getDecisionNote());
        }
    }
    public record Page(List<View> items, int page, int size, long totalElements) { }
}
