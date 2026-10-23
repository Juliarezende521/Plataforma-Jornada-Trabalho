package io.github.juliarezende521.workjourney.schedule;

import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

@Profile("!bootstrap")
interface WorkScheduleExceptionRepository extends JpaRepository<WorkScheduleException,UUID> {
    List<WorkScheduleException> findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAscCreatedAtUtcAsc(UUID employeeId,LocalDate from,LocalDate to);
    Page<WorkScheduleException> findByEmployee_Id(UUID employeeId,Pageable pageable);
}
