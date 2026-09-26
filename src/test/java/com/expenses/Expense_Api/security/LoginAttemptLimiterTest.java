package com.expenses.Expense_Api.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-26T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterTooManyFailuresThenReleasesAfterWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isZero();
            limiter.recordFailure("arpit", "1.1.1.1");
        }
        assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isEqualTo(15 * 60);
        // Same username differing only in case or spaces counts as the same account.
        assertThat(limiter.secondsUntilAllowed(" Arpit ", "1.1.1.1")).isPositive();

        clock.now = clock.now.plus(LoginAttemptLimiter.WINDOW);
        assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isZero();
    }

    @Test
    void otherAddressesAndAccountsAreNotAffected() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, new MutableClock());
        limiter.recordFailure("arpit", "1.1.1.1");
        limiter.recordFailure("arpit", "1.1.1.1");

        assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isPositive();
        assertThat(limiter.secondsUntilAllowed("arpit", "2.2.2.2")).isZero();
        assertThat(limiter.secondsUntilAllowed("someone", "1.1.1.1")).isZero();
    }

    @Test
    void successClearsFailures() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, clock);
        limiter.recordFailure("arpit", "1.1.1.1");
        limiter.recordSuccess("arpit", "1.1.1.1");
        limiter.recordFailure("arpit", "1.1.1.1");

        assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isZero();
    }

    @Test
    void failuresAfterTheWindowStartANewCount() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, clock);
        limiter.recordFailure("arpit", "1.1.1.1");
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        limiter.recordFailure("arpit", "1.1.1.1");

        assertThat(limiter.secondsUntilAllowed("arpit", "1.1.1.1")).isZero();
    }
}
