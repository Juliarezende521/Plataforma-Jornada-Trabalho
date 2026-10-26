package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.closing.MonthlyClosureGuard;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.WorkState;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import io.github.juliarezende521.workjourney.team.ManagerAuthorization;
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
    private final HrAuthorization hrAuthorization;
    private final ManagerAuthorization managerAuthorization;
    private final MonthlyClosureGuard closureGuard;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore sessions;
    private final WorkAdjustmentRepository requests;
    private final AuditEntryRepository audits;
    private final EffectiveTimelineService timelines;
    private final Clock clock;

    public WorkAdjustmentService(CurrentEmployeeIdentity identity, HrAuthorization hrAuthorization,
                                 ManagerAuthorization managerAuthorization,
                                 MonthlyClosureGuard closureGuard, EmployeeRepository employees,
                                 JpaWorkSessionStore sessions, WorkAdjustmentRepository requests,
                                 AuditEntryRepository audits, EffectiveTimelineService timelines, Clock clock) {
        this.identity=identity; this.hrAuthorization=hrAuthorization; this.managerAuthorization=managerAuthorization;
        this.closureGuard=closureGuard;
        this.employees=employees; this.sessions=sessions;
        this.requests=requests; this.audits=audits; this.timelines=timelines; this.clock=clock;
    }

    @Transactional(timeout = 15)
    public View request(UUID sessionId, WorkEventType type, Instant proposedAt, String reason) {
        var actor=identity.require();
        var employee=employees.lockOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        var session=sessions.findOwned(sessionId, employee.getId()).orElseThrow(AdjustmentNotVisible::new).session();
        closureGuard.requireOpen(employee.getId(), session.getWorkDate());
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
        hrAuthorization.require();
        var result=requests.findByStatusOrderByRequestedAtUtcAscIdAsc(AdjustmentStatus.PENDING, PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(View::from).toList(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page teamPending(int page, int size) {
        validatePage(page,size);
        var context=managerAuthorization.requireManager();
        var result=requests.findCurrentTeamPending(context.actor().employeeId(), context.referenceDate(),
                AdjustmentStatus.PENDING, PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(View::from).toList(), page, size, result.getTotalElements());
    }

    @Transactional(timeout = 15)
    public View decide(UUID id, AdjustmentStatus decision, String note) {
        var actor=hrAuthorization.require();
        var request=requests.lockById(id).orElseThrow(AdjustmentNotVisible::new);
        return decideLocked(request, decision, note, actor.userId());
    }

    @Transactional(timeout = 15)
    public View decideTeam(UUID id, AdjustmentStatus decision, String note) {
        var context=managerAuthorization.requireManager();
        var request=requests.lockCurrentTeamRequest(id, context.actor().employeeId(), context.referenceDate())
                .orElseThrow(AdjustmentNotVisible::new);
        return decideLocked(request, decision, note, context.actor().userId());
    }

    private View decideLocked(WorkAdjustmentRequest request, AdjustmentStatus decision, String note, UUID actorUserId) {
        String validNote=text(note, 0, 500, false);
        Instant now=now();
        var employee=employees.lockActive(request.getEmployee().getId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        if (decision==AdjustmentStatus.APPROVED) {
            var stored=sessions.findOwned(request.getWorkSessionId(),employee.getId()).orElseThrow(AdjustmentNotVisible::new);
            var session=stored.session();
            var effective=timelines.projectIncluding(session,request,now);
            if(effective.state()!=session.getState()) {
                boolean updatesOpenCheckpoint=session.getState()!=WorkState.CLOSED
                        && request.getRequestedEventType()!=WorkEventType.ENTRY
                        && !request.getProposedOccurredAtUtc().isBefore(session.getLastEventAtUtc());
                if(!updatesOpenCheckpoint)throw new AdjustmentConflict();
                var corrected=sessions.appendCorrection(session.getId(),employee.getId(),stored.version(),request.getRequestedEventType(),
                        request.getProposedOccurredAtUtc(),now,session.getReferenceTimeZoneId()).session();
                if(corrected.getState()!=effective.state())throw new AdjustmentConflict();
                request.markApplied(corrected.getEvents().get(corrected.getEvents().size()-1).id());
            }
        }
        request.decide(decision, actorUserId, now, validNote);
        audits.save(new AuditEntry(actorUserId, decision==AdjustmentStatus.APPROVED
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
                       Instant decidedAtUtc, String decisionNote,UUID appliedEventId) {
        static View from(WorkAdjustmentRequest request) {
            return new View(request.getId(), request.getWorkSessionId(), request.getEmployee().getId(),
                    request.getEmployee().getName(), request.getRequestedEventType(), request.getProposedOccurredAtUtc(),
                    request.getReason(), request.getStatus(), request.getRequestedAtUtc(),
                    request.getDecidedAtUtc(), request.getDecisionNote(),request.getAppliedEventId());
        }
    }
    public record Page(List<View> items, int page, int size, long totalElements) { }
}
