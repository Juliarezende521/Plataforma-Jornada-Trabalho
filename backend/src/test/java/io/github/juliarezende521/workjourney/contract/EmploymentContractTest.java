package io.github.juliarezende521.workjourney.contract;

import io.github.juliarezende521.workjourney.account.AppUser;
import io.github.juliarezende521.workjourney.account.Employee;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmploymentContractTest {
    private final Employee employee = new Employee(
            new AppUser("worker@example.invalid", "$2a$12$" + "A".repeat(53)), "Worker", "UTC");

    @Test
    void normalizesAndPreservesVersion() {
        var contract = new EmploymentContract(employee, LocalDate.of(2026, 11, 1), " br ",
                EmploymentContract.Type.FULL_TIME, 2400, UUID.randomUUID(), Instant.EPOCH);

        assertEquals("BR", contract.countryCode());
        assertEquals(2400, contract.weeklyMinutes());
    }

    @Test
    void rejectsUnknownCountryAndInvalidMinutes() {
        assertThrows(InvalidContract.class, () -> new EmploymentContract(employee, LocalDate.now(), "XX",
                EmploymentContract.Type.FULL_TIME, 2400, UUID.randomUUID(), Instant.EPOCH));
        assertThrows(InvalidContract.class, () -> new EmploymentContract(employee, LocalDate.now(), "BR",
                EmploymentContract.Type.FULL_TIME, 0, UUID.randomUUID(), Instant.EPOCH));
    }
}
