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
    @Autowired javax.sql.DataSource dataSource;

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

    @Test
    void databaseRejectsDuplicateEmailAndOrphanEmployee() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String email = java.util.UUID.randomUUID() + "@example.invalid";
                try (var insert = connection.prepareStatement("INSERT INTO dbo.app_user (id,email,password_hash,active) VALUES (?,?,?,1)")) {
                    insert.setString(1, java.util.UUID.randomUUID().toString());
                    insert.setString(2, email);
                    insert.setString(3, encoder.encode("test-password-only"));
                    insert.executeUpdate();
                    insert.setString(1, java.util.UUID.randomUUID().toString());
                    var duplicate = assertThrows(java.sql.SQLException.class, insert::executeUpdate);
                    assertEquals(2627, duplicate.getErrorCode());
                }
                connection.rollback();
                try (var insert = connection.prepareStatement("INSERT INTO dbo.employee (id,user_id,name,reference_time_zone_id) VALUES (?,?,?,?)")) {
                    insert.setString(1, java.util.UUID.randomUUID().toString());
                    insert.setString(2, java.util.UUID.randomUUID().toString());
                    insert.setNString(3, "João avaliação");
                    insert.setString(4, "Europe/Amsterdam");
                    var orphan = assertThrows(java.sql.SQLException.class, insert::executeUpdate);
                    assertEquals(547, orphan.getErrorCode());
                }
            } finally { connection.rollback(); }
        }
    }
}
