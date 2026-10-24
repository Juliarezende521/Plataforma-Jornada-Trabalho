package io.github.juliarezende521.workjourney.contract;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.AuditEntry;
import io.github.juliarezende521.workjourney.adjustment.AuditEntryRepository;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class EmploymentContractService {
    private final HrAuthorization hrAuthorization;
    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final AuditEntryRepository audits;
    private final Clock clock;

    EmploymentContractService(HrAuthorization hrAuthorization, EmployeeRepository employees,
                              EmploymentContractRepository contracts, AuditEntryRepository audits, Clock clock) {
        this.hrAuthorization = hrAuthorization;
        this.employees = employees;
        this.contracts = contracts;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    View currentForHr(UUID employeeId, LocalDate on) {
        hrAuthorization.require();
        var employee = employees.findActiveById(employeeId).orElseThrow(NotVisible::new);
        var date = on == null
                ? UtcInstants.normalize(clock.instant())
                        .atZone(ZoneId.of(employee.getReferenceTimeZoneId())).toLocalDate()
                : on;
        return contracts.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, date)
                .map(View::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    Page history(UUID employeeId, int page, int size) {
        hrAuthorization.require();
        validate(page, size);
        employees.findActiveById(employeeId).orElseThrow(NotVisible::new);
        var result = contracts.findByEmployee_Id(employeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveFrom", "id")));
        return new Page(result.map(View::from).getContent(), page, size, result.getTotalElements());
    }

    @Transactional(timeout = 15)
    View create(UUID employeeId, LocalDate effectiveFrom, String countryCode,
                EmploymentContract.Type type, int weeklyMinutes) {
        var actor = hrAuthorization.require();
        var employee = employees.lockActive(employeeId).orElseThrow(NotVisible::new);
        if (contracts.existsByEmployee_IdAndEffectiveFrom(employeeId, effectiveFrom)) {
            throw new InvalidContract(409);
        }
        var now = UtcInstants.normalize(clock.instant());
        var contract = contracts.save(new EmploymentContract(employee, effectiveFrom, countryCode,
                type, weeklyMinutes, actor.userId(), now));
        audits.save(new AuditEntry(actor.userId(), "EMPLOYMENT_CONTRACT_CREATED", "EMPLOYMENT_CONTRACT",
                contract.id(), now, "effectiveFrom=" + effectiveFrom + ",countryCode=" + contract.countryCode()
                        + ",employmentType=" + type + ",weeklyMinutes=" + weeklyMinutes));
        return View.from(contract);
    }

    private static void validate(int page, int size) {
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) {
            throw new InvalidContract();
        }
    }

    record View(UUID id, UUID employeeId, LocalDate effectiveFrom, String countryCode,
                String employmentType, int weeklyMinutes, Instant createdAtUtc) {
        static View from(EmploymentContract contract) {
            return new View(contract.id(), contract.employee().getId(), contract.effectiveFrom(),
                    contract.countryCode(), contract.type().name(), contract.weeklyMinutes(), contract.createdAt());
        }
    }

    record Page(List<View> items, int page, int size, long totalElements) { }
}
