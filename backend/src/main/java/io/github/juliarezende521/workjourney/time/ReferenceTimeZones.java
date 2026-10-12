package io.github.juliarezende521.workjourney.time;

import java.time.ZoneId;
import java.util.Set;

/** Shared reference-zone invariant for employees and journey snapshots. */
public final class ReferenceTimeZones {
    private static final Set<String> AVAILABLE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private ReferenceTimeZones() { }

    public static ZoneId requireValid(String id) {
        if (id == null || !AVAILABLE_IDS.contains(id)) {
            throw new IllegalArgumentException("An IANA reference zone is required");
        }
        return ZoneId.of(id);
    }
}
