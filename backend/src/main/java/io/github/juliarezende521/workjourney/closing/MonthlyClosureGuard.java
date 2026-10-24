package io.github.juliarezende521.workjourney.closing;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class MonthlyClosureGuard {
    private final MonthlyClosureRepository closures;

    MonthlyClosureGuard(MonthlyClosureRepository closures) {
        this.closures = closures;
    }

    public void requireOpen(UUID employeeId, LocalDate workDate) {
        if (closures.existsByEmployee_IdAndCompetenceYearAndCompetenceMonth(
                employeeId, (short) workDate.getYear(), (byte) workDate.getMonthValue())) {
            throw new InvalidMonthlyClosure(409, "PERIOD_CLOSED");
        }
    }

    public void requireScheduleChangeAllowed(UUID employeeId, LocalDate effectiveDate) {
        if (closures.existsByEmployee_IdAndPeriodToGreaterThanEqual(employeeId, effectiveDate)) {
            throw new InvalidMonthlyClosure(409, "PERIOD_CLOSED");
        }
    }
}
