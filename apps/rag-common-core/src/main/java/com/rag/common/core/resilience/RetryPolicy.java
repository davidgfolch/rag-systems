package com.rag.common.core.resilience;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential-backoff retry schedule with equal jitter. Attempt counts are
 * one-based: {@code nextDelay(1)} is the wait after the first failure.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff,
                          double multiplier, double jitter) {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;
    public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(10);
    public static final double DEFAULT_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER = 0.5;
    private static final double FULL_JITTER = 1.0;
    private static final int NO_RETRY_ATTEMPTS = 1;

    public RetryPolicy {
        if (maxAttempts < NO_RETRY_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        requireNonNegative(initialBackoff, "initialBackoff");
        requireNonNegative(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be shorter than initialBackoff");
        }
        if (multiplier < FULL_JITTER) {
            throw new IllegalArgumentException("multiplier must be at least 1");
        }
        if (jitter < 0 || jitter > FULL_JITTER) {
            throw new IllegalArgumentException("jitter must be between 0 and 1");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF,
                DEFAULT_MAX_BACKOFF, DEFAULT_MULTIPLIER, DEFAULT_JITTER);
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(NO_RETRY_ATTEMPTS, Duration.ZERO, Duration.ZERO,
                DEFAULT_MULTIPLIER, 0);
    }

    /**
     * Delay before the retry that follows {@code failedAttempt} (1-based).
     * Jitter keeps the lower bound at {@code capped * (1 - jitter)}.
     */
    public Duration nextDelay(int failedAttempt) {
        int attempt = Math.max(failedAttempt, NO_RETRY_ATTEMPTS);
        double exponential = initialBackoff.toMillis() * Math.pow(multiplier, (double) attempt - NO_RETRY_ATTEMPTS);
        long capped = Math.min((long) exponential, maxBackoff.toMillis());
        long jitterMs = (long) (capped * jitter);
        long low = capped - jitterMs;
        long chosen = jitterMs == 0 ? capped : ThreadLocalRandom.current().nextLong(low, capped + 1);
        return Duration.ofMillis(Math.max(chosen, 0));
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
