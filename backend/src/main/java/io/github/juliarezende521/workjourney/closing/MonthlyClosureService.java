package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class MonthlyClosureService {
    private final HrAuthorization hrAuthorization;
    private final EmployeeRepository employees;
    private final MonthlyClosureRepository closures;
    private final WorkAdjustmentRepository adjustments;
    private final JpaWorkSessionStore sessions;
    private final EffectiveTimelineService timelines;
    private final WorkSchedulePlan schedules;
    private final AuditEntryRepository audits;
    private final Clock clock;

    MonthlyClosureService(HrAuthorization hrAuthorization, EmployeeRepository employees,
                          MonthlyClosureRepository closures, WorkAdjustmentRepository adjustments,
                          JpaWorkSessionStore sessions, EffectiveTimelineService timelines,
                          WorkSchedulePlan schedules, AuditEntryRepository audits, Clock clock) {
        this.hrAuthorization = hrAuthorization;
        this.employees = employees;
        this.closures = closures;
        this.adjustments = adjustments;
        this.sessions = sessions;
        this.timelines = timelines;
        this.schedules = schedules;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    View find(UUID employeeId, YearMonth competence) {
        hrAuthorization.require();
        employees.findActiveById(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        return closures.findByEmployee_IdAndCompetenceYearAndCompetenceMonth(
                employeeId, year(competence), month(competence)).map(View::from).orElse(null);
    }

    @Transactional(readOnly = true)
    Page history(UUID employeeId, int page, int size) {
        hrAuthorization.require();
        validatePage(page, size);
        employees.findActiveById(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var result = closures.findByEmployee_Id(employeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,
                        "competenceYear", "competenceMonth", "id")));
        return new Page(result.map(View::from).getContent(), page, size, result.getTotalElements());
    }

    @Transactional(timeout = 30)
    View close(UUID employeeId, YearMonth competence) {
        var actor = hrAuthorization.require();
        var employee = employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        short competenceYear = year(competence);
        byte competenceMonth = month(competence);
        var now = UtcInstants.normalize(clock.instant());
        var today = now.atZone(ZoneId.of(employee.getReferenceTimeZoneId())).toLocalDate();
        if (!competence.atEndOfMonth().isBefore(today)) {
            throw new InvalidMonthlyClosure(409, "PERIOD_NOT_FINISHED");
        }
        if (closures.existsByEmployee_IdAndCompetenceYearAndCompetenceMonth(
                employeeId, competenceYear, competenceMonth)) {
            throw new InvalidMonthlyClosure(409, "ALREADY_CLOSED");
        }
        var from = competence.atDay(1);
        var to = competence.atEndOfMonth();
        var summary = JourneyQueries.summarize(sessions, timelines, schedules, employeeId, from, to, now);
        if (summary.openSessions() > 0) {
            throw new InvalidMonthlyClosure(409, "OPEN_SESSIONS");
        }
        if (summary.scheduledDays() != summary.periodDays()) {
            throw new InvalidMonthlyClosure(409, "INCOMPLETE_SCHEDULE");
        }
        if (adjustments.countInPeriod(employeeId, AdjustmentStatus.PENDING, from, to) > 0) {
            throw new InvalidMonthlyClosure(409, "PENDING_ADJUSTMENTS");
        }
        var closure = closures.save(new MonthlyClosure(employee, competence, summary, actor.userId(), now));
        audits.save(new AuditEntry(actor.userId(), "MONTHLY_CLOSURE_CREATED", "MONTHLY_CLOSURE",
                closure.id(), now, "employeeId=" + employeeId + ",competence=" + competence
                        + ",balanceSeconds=" + closure.balanceSeconds()));
        return View.from(closure);
    }

    private static short year(YearMonth competence) {
        if (competence == null || competence.getYear() < 1 || competence.getYear() > 9999) {
            throw new InvalidMonthlyClosure();
        }
        return (short) competence.getYear();
    }

    private static byte month(YearMonth competence) {
        return (byte) competence.getMonthValue();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) {
            throw new InvalidMonthlyClosure();
        }
    }

    record View(UUID id, UUID employeeId, String competence, LocalDate periodFrom, LocalDate periodTo,
                long totalWorkSeconds, long plannedWorkSeconds, long balanceSeconds, long toleranceSeconds,
                boolean withinTolerance, long totalSessions, long adjustedSessions, int workedDays,
                String status, Instant closedAtUtc) {
        static View from(MonthlyClosure value) {
            return new View(value.id(), value.employeeId(), value.competence().toString(), value.periodFrom(),
                    value.periodTo(), value.totalWorkSeconds(), value.plannedWorkSeconds(),
                    value.balanceSeconds(), value.toleranceSeconds(), value.withinTolerance(),
                    value.totalSessions(), value.adjustedSessions(), value.workedDays(), "CLOSED", value.closedAt());
        }
    }

    record Page(List<View> items, int page, int size, long totalElements) { }
}
