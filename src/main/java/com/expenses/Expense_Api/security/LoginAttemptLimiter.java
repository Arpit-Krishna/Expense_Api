package com.expenses.Expense_Api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after too many failed logins for the same username from the same
 * address, further attempts are refused until the window passes. Kept in memory, which is enough
 * for a single instance; a restart clears it.
 */
@Component
public class LoginAttemptLimiter {
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private record Attempts(int failures, Instant windowStart) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter(@Value("${app.auth.max-failed-logins:10}") int maxFailures) {
        this(maxFailures, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailures, Clock clock) {
        this.maxFailures = maxFailures;
        this.clock = clock;
    }

    /** Seconds until the caller may try again, or 0 when they are not blocked. */
    public long secondsUntilAllowed(String username, String clientIp) {
        Attempts a = attempts.get(key(username, clientIp));
        if (a == null || a.failures() < maxFailures) return 0;
        Duration left = Duration.between(clock.instant(), a.windowStart().plus(WINDOW));
        return left.isNegative() || left.isZero() ? 0 : Math.max(1, left.toSeconds());
    }

    public void recordFailure(String username, String clientIp) {
        if (attempts.size() > MAX_TRACKED) evictExpired();
        Instant now = clock.instant();
        attempts.compute(key(username, clientIp), (k, a) ->
                a == null || expired(a, now) ? new Attempts(1, now) : new Attempts(a.failures() + 1, a.windowStart()));
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    private void evictExpired() {
        Instant now = clock.instant();
        attempts.values().removeIf(a -> expired(a, now));
    }

    private static boolean expired(Attempts a, Instant now) {
        return !now.isBefore(a.windowStart().plus(WINDOW));
    }

    private static String key(String username, String clientIp) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return user + "|" + (clientIp == null ? "" : clientIp);
    }
}
