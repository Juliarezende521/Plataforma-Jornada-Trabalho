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
        var v5 = configuration().target("5").load();
        assertEquals(4, v5.migrate().migrationsExecuted);
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO dbo.employee(id,user_id,name,reference_time_zone_id) VALUES ('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000005',N'Upgrade','America/Sao_Paulo')");
            statement.executeUpdate("INSERT INTO dbo.work_session(id,employee_id,author_user_id,work_date,reference_time_zone_id,state,opened_at_utc,last_event_at_utc,version) VALUES ('00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000005','2026-10-14','America/Sao_Paulo','WORKING','2026-10-14T14:00:00+00:00','2026-10-14T14:00:00+00:00',0)");
            statement.executeUpdate("INSERT INTO dbo.work_event(id,work_session_id,author_user_id,sequence,event_type,occurred_at_utc,recorded_at_utc,recorded_time_zone_id) VALUES ('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000005',1,'ENTRY','2026-10-14T14:00:00+00:00','2026-10-14T14:00:00+00:00','Europe/Lisbon')");
        }
        var latest = configuration().load();
        assertEquals(11, latest.migrate().migrationsExecuted);
        assertEquals("16", latest.info().current().getVersion().getVersion());
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"));
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT hr_reader FROM dbo.app_user WHERE email='upgrade@example.invalid'")) {
                assertTrue(rows.next()); assertFalse(rows.getBoolean(1));
            }
            try (var rows = statement.executeQuery("SELECT work_mode,recorded_time_zone_id FROM dbo.work_event WHERE id='00000000-0000-0000-0000-000000000008'")) {
                assertTrue(rows.next()); assertNull(rows.getString(1)); assertEquals("Europe/Lisbon", rows.getString(2));
            }
            statement.executeUpdate("DELETE FROM dbo.work_event WHERE id='00000000-0000-0000-0000-000000000008'");
            statement.executeUpdate("DELETE FROM dbo.work_session WHERE id='00000000-0000-0000-0000-000000000007'");
            statement.executeUpdate("DELETE FROM dbo.employee WHERE id='00000000-0000-0000-0000-000000000006'");
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
