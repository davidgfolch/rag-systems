package com.rag.common.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Fair, bounded concurrency gate. Callers acquire a permit before dispatching
 * work and release it when the work finishes, so expensive downstream calls
 * (providers, databases) never see an unbounded burst.
 */
public final class ConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiter.class);

    private final String name;
    private final int maxConcurrent;
    private final Duration waitTimeout;
    private final Semaphore permits;

    public ConcurrencyLimiter(String name, int maxConcurrent, Duration waitTimeout) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be at least 1");
        }
        if (waitTimeout == null || waitTimeout.isNegative()) {
            throw new IllegalArgumentException("waitTimeout must not be negative");
        }
        this.name = name;
        this.maxConcurrent = maxConcurrent;
        this.waitTimeout = waitTimeout;
        this.permits = new Semaphore(maxConcurrent, true);
    }

    /**
     * Waits up to the configured timeout for a permit. Returns {@code false}
     * when the gate stayed saturated (including when the caller is interrupted,
     * in which case the interrupt flag is restored).
     */
    public boolean tryEnter() {
        try {
            boolean acquired = permits.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Concurrency gate '{}' still saturated after {} ms ({} in flight)",
                        name, waitTimeout.toMillis(), inFlight());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void exit() {
        permits.release();
    }

    public int inFlight() {
        return maxConcurrent - permits.availablePermits();
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public Duration waitTimeout() {
        return waitTimeout;
    }
}
