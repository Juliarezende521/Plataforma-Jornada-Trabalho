package io.github.juliarezende521.workjourney.closing;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MonthlyClosureGuardTest {
    private final MonthlyClosureRepository closures = mock(MonthlyClosureRepository.class);
    private final MonthlyClosureGuard guard = new MonthlyClosureGuard(closures);
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void preventsAdjustmentInsideClosedCompetence() {
        when(closures.existsByEmployee_IdAndCompetenceYearAndCompetenceMonth(
                employeeId, (short) 2026, (byte) 10)).thenReturn(true);

        assertThrows(InvalidMonthlyClosure.class,
                () -> guard.requireOpen(employeeId, LocalDate.of(2026, 10, 15)));
    }

    @Test
    void preventsRetroactiveScheduleChangeThatWouldAffectClosure() {
        var effectiveDate = LocalDate.of(2026, 10, 1);
        when(closures.existsByEmployee_IdAndPeriodToGreaterThanEqual(employeeId, effectiveDate)).thenReturn(true);

        assertThrows(InvalidMonthlyClosure.class,
                () -> guard.requireScheduleChangeAllowed(employeeId, effectiveDate));
    }
}
