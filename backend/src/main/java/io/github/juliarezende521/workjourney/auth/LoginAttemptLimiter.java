package io.github.juliarezende521.workjourney.auth;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded fixed-window limiter for one application instance, independent of HTTP. */
public final class LoginAttemptLimiter {
    public static final int WINDOW_SECONDS = 60;
    private static final long WINDOW_MILLIS = WINDOW_SECONDS * 1000L;
    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_ADDRESSES = 4096;
    private final Map<String, Window> attempts = new HashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public synchronized boolean allow(String address) {
        Objects.requireNonNull(address, "Remote address is required");
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().started >= WINDOW_MILLIS);
        Window window = attempts.get(address);
        if (window == null) {
            if (attempts.size() >= MAX_ADDRESSES) return false;
            window = new Window(now);
            attempts.put(address, window);
        }
        // Do not increment rejected attempts indefinitely: a saturated counter cannot wrap.
        if (window.count >= MAX_ATTEMPTS) return false;
        window.count++;
        return true;
    }

    private static final class Window {
        private final long started;
        private int count;

        private Window(long started) { this.started = started; }
    }
}
