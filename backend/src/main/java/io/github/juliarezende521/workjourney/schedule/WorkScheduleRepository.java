package io.github.juliarezende521.workjourney.schedule;

import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
interface WorkScheduleRepository extends JpaRepository<WorkSchedule,UUID> {
    Optional<WorkSchedule> findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(UUID employeeId,LocalDate on);
    List<WorkSchedule> findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
            UUID employeeId,LocalDate after,LocalDate onOrBefore);
    boolean existsByEmployee_IdAndEffectiveFrom(UUID employeeId,LocalDate effectiveFrom);
}
