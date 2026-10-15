package io.github.juliarezende521.workjourney.journey;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Run first on the disposable empty CI database, without booting current JPA entities. */
class MigrationUpgradeSqlServerIT {
    @Test
    void migratesEmptyDatabaseToV1ThenUpgradesToLatestWithoutEditingHistory() throws Exception {
        var v1 = configuration().target("1").load();
        assertEquals(1, v1.migrate().migrationsExecuted);
        assertEquals("1", v1.info().current().getVersion().getVersion());
        // A pre-existing V1 account must not gain HR access during upgrade.
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO dbo.app_user (id,email,password_hash,active) VALUES ('00000000-0000-0000-0000-000000000005','upgrade@example.invalid','" + "$2a$12$" + "a".repeat(53) + "',1)");
        }
        var latest = configuration().load();
        assertEquals(4, latest.migrate().migrationsExecuted);
        assertEquals("5", latest.info().current().getVersion().getVersion());
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"));
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT hr_reader FROM dbo.app_user WHERE email='upgrade@example.invalid'")) {
                assertTrue(rows.next()); assertFalse(rows.getBoolean(1));
            }
            statement.executeUpdate("DELETE FROM dbo.app_user WHERE email='upgrade@example.invalid'");
        }
        latest.validate();
        assertEquals(0, latest.migrate().migrationsExecuted);
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration() {
        return Flyway.configure().dataSource(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"))
                .locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false);
    }
}
