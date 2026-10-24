package io.github.juliarezende521.workjourney.closing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
interface MonthlyClosureRepository extends JpaRepository<MonthlyClosure, UUID> {
    Optional<MonthlyClosure> findByEmployee_IdAndCompetenceYearAndCompetenceMonth(
            UUID employeeId, short year, byte month);
    boolean existsByEmployee_IdAndCompetenceYearAndCompetenceMonth(UUID employeeId, short year, byte month);
    boolean existsByEmployee_IdAndPeriodToGreaterThanEqual(UUID employeeId, java.time.LocalDate date);
    Page<MonthlyClosure> findByEmployee_Id(UUID employeeId, Pageable pageable);
}
