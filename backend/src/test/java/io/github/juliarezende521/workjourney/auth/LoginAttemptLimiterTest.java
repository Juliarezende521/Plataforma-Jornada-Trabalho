package io.github.juliarezende521.workjourney.auth;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptLimiterTest {
    @Test
    void limitsEachAddressIndependently() {
        var limiter = new LoginAttemptLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) assertTrue(limiter.allow("192.0.2.1"));
        for (int i = 0; i < 100; i++) assertFalse(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.2"));
    }

    @Test
    void windowExpiresAfterOneMinute() {
        var time = new java.util.concurrent.atomic.AtomicLong();
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(time.get()); }
        };
        var limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < 10; i++) assertTrue(limiter.allow("192.0.2.1"));
        time.set(59_999);
        assertFalse(limiter.allow("192.0.2.1"));
        time.set(60_000);
        assertTrue(limiter.allow("192.0.2.1"));
    }

    @Test
    void boundsAddressesWithoutEvictingActiveWindows() {
        var limiter = new LoginAttemptLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 4096; i++) assertTrue(limiter.allow("address-" + i));
        assertFalse(limiter.allow("address-over-capacity"));
        assertTrue(limiter.allow("address-0"));
    }
}
