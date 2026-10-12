package io.github.juliarezende521.workjourney.time;

import java.time.Instant;
import java.util.Objects;

/** Domain precision compatible with SQL Server datetimeoffset(7), without JDBC coupling. */
public final class UtcInstants {
    private static final int STORAGE_TICK_NANOS = 100;

    private UtcInstants() { }

    public static Instant normalize(Instant instant) {
        Objects.requireNonNull(instant, "Server instant is required");
        return instant.minusNanos(instant.getNano() % STORAGE_TICK_NANOS);
    }

    public static boolean hasStoragePrecision(Instant instant) {
        return instant.getNano() % STORAGE_TICK_NANOS == 0;
    }
}
