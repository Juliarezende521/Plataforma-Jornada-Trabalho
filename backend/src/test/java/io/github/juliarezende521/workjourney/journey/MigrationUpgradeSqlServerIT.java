package io.github.juliarezende521.workjourney.journey;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Run first on the disposable empty CI database, without booting current JPA entities. */
class MigrationUpgradeSqlServerIT {
    @Test
    void migratesEmptyDatabaseToV1ThenUpgradesToLatestWithoutEditingHistory() {
        var v1 = configuration().target("1").load();
        assertEquals(1, v1.migrate().migrationsExecuted);
        assertEquals("1", v1.info().current().getVersion().getVersion());
        var latest = configuration().load();
        assertEquals(3, latest.migrate().migrationsExecuted);
        assertEquals("4", latest.info().current().getVersion().getVersion());
        latest.validate();
        assertEquals(0, latest.migrate().migrationsExecuted);
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configuration() {
        return Flyway.configure().dataSource(System.getenv("DB_URL"),
                System.getenv("DB_MIGRATION_USERNAME"), System.getenv("DB_MIGRATION_PASSWORD"))
                .locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false);
    }
}
