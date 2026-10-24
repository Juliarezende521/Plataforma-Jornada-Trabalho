package io.github.juliarezende521.workjourney.contract;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    Optional<EmploymentContract> findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID employeeId, LocalDate date);

    Page<EmploymentContract> findByEmployee_Id(UUID employeeId, Pageable pageable);

    boolean existsByEmployee_IdAndEffectiveFrom(UUID employeeId, LocalDate effectiveFrom);
}
