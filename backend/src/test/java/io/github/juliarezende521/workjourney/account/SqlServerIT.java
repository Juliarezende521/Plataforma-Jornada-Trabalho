package io.github.juliarezende521.workjourney.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: ./mvnw -Dtest=SqlServerIT test with real DB_* environment variables. */
@SpringBootTest
@Transactional
class SqlServerIT {
    @Autowired AppUserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void persistsUnicodeAndIanaZoneThenReloadsAccountGraph() {
        String email = java.util.UUID.randomUUID() + "@example.invalid";
        var account = users.save(new AppUser(email, encoder.encode("test-password-only")));
        var employee = employees.save(new Employee(account, "João avaliação", "America/Sao_Paulo"));
        employees.flush();
        entityManager.clear();
        var loaded = users.findByEmail(email).orElseThrow();
        assertEquals(employee.getId(), loaded.getEmployee().getId());
        assertEquals("João avaliação", loaded.getEmployee().getName());
        assertEquals("America/Sao_Paulo", loaded.getEmployee().getReferenceTimeZoneId());
        assertTrue(encoder.matches("test-password-only", loaded.getPasswordHash()));
    }
}
