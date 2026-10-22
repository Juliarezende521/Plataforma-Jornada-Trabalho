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

    WorkSchedulePlan(WorkScheduleRepository schedules) {
        this.schedules = schedules;
    }

    public Plan calculate(UUID employeeId, LocalDate from, LocalDate to) {
        var versions = new java.util.ArrayList<WorkSchedule>();
        schedules.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, from)
                .ifPresent(versions::add);
        versions.addAll(schedules.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, from, to));
        long total = 0;
        int coveredDays = 0;
        int next = 0;
        WorkSchedule current = null;
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            while (next < versions.size() && !versions.get(next).effectiveFrom().isAfter(date)) {
                current = versions.get(next++);
            }
            if (current != null) {
                total += current.minutes().get(date.getDayOfWeek().getValue() - 1);
                coveredDays++;
            }
        }
        return new Plan(total, coveredDays);
    }

    public record Plan(long minutes, int coveredDays) { }
}
