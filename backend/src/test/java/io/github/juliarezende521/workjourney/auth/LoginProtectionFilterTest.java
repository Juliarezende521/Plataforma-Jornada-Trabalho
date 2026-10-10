package io.github.juliarezende521.workjourney.auth;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginProtectionFilterTest {
    @Test
    void limitsEachAddressIndependently() {
        var filter = new LoginProtectionFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) assertTrue(filter.allow("192.0.2.1"));
        assertFalse(filter.allow("192.0.2.1"));
        assertTrue(filter.allow("192.0.2.2"));
    }

    @Test
    void windowExpiresAfterOneMinute() {
        var time = new java.util.concurrent.atomic.AtomicLong();
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(time.get()); }
        };
        var filter = new LoginProtectionFilter(clock);
        for (int i = 0; i < 10; i++) assertTrue(filter.allow("192.0.2.1"));
        assertFalse(filter.allow("192.0.2.1"));
        time.set(60_000);
        assertTrue(filter.allow("192.0.2.1"));
    }
}
