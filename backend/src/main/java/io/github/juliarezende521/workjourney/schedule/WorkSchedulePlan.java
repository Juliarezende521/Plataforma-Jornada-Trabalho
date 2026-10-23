package io.github.juliarezende521.workjourney.schedule;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Calculates planned time from the initial version and changes inside the bounded requested period. */
@Service
@Profile("!bootstrap")
public class WorkSchedulePlan {
    private final WorkScheduleRepository schedules;
    private final WorkScheduleExceptionRepository exceptions;

    WorkSchedulePlan(WorkScheduleRepository schedules,WorkScheduleExceptionRepository exceptions) {
        this.schedules = schedules;
        this.exceptions=exceptions;
    }

    public Plan calculate(UUID employeeId, LocalDate from, LocalDate to) {
        var versions = new java.util.ArrayList<WorkSchedule>();
        schedules.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, from)
                .ifPresent(versions::add);
        versions.addAll(schedules.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, from, to));
        var overrides=new java.util.HashMap<LocalDate,WorkScheduleException>();
        for(var exception:exceptions.findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAscCreatedAtUtcAsc(employeeId,from,to))
            overrides.put(exception.workDate(),exception);
        long total = 0;
        long tolerance = 0;
        int coveredDays = 0;
        int next = 0;
        WorkSchedule current = null;
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            while (next < versions.size() && !versions.get(next).effectiveFrom().isAfter(date)) {
                current = versions.get(next++);
            }
            var override=overrides.get(date);
            if (current != null||override!=null) {
                int planned=override==null?current.minutes().get(date.getDayOfWeek().getValue() - 1):override.plannedMinutes();
                total += planned;
                if(planned>0)tolerance+=override==null?current.toleranceMinutes():override.toleranceMinutes();
                coveredDays++;
            }
        }
        return new Plan(total, tolerance, coveredDays);
    }

    public record Plan(long minutes, long toleranceMinutes, int coveredDays) { }
}
