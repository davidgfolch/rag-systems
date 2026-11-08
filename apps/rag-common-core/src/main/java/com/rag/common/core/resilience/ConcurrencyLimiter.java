package com.rag.common.core.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Fair, bounded concurrency gate. Callers acquire a permit before dispatching
 * work and release it when the work finishes, so expensive downstream calls
 * (providers, databases) never see an unbounded burst.
 *
 * <p>When a {@link MeterRegistry} is supplied the gate also reports in-flight
 * permits, rejected callers and permit wait time.
 */
public final class ConcurrencyLimiter {

    public static final String IN_FLIGHT_METRIC = "rag.resilience.limiter.in_flight";
    public static final String REJECTIONS_METRIC = "rag.resilience.limiter.rejections";
    public static final String WAIT_METRIC = "rag.resilience.limiter.wait";
    private static final String NAME_TAG = "name";

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiter.class);

    private final String name;
    private final int maxConcurrent;
    private final Duration waitTimeout;
    private final Semaphore permits;
    private final Timer waitTimer;
    private final Counter rejections;

    public ConcurrencyLimiter(String name, int maxConcurrent, Duration waitTimeout) {
        this(name, maxConcurrent, waitTimeout, null);
    }

    public ConcurrencyLimiter(String name, int maxConcurrent, Duration waitTimeout, MeterRegistry registry) {
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
        this.waitTimer = registry == null ? null : Timer.builder(WAIT_METRIC)
                .tag(NAME_TAG, name)
                .description("Time callers waited for a permit")
                .register(registry);
        this.rejections = registry == null ? null : Counter.builder(REJECTIONS_METRIC)
                .tag(NAME_TAG, name)
                .description("Callers rejected because the gate stayed saturated")
                .register(registry);
        if (registry != null) {
            Gauge.builder(IN_FLIGHT_METRIC, this, ConcurrencyLimiter::inFlight)
                    .tag(NAME_TAG, name)
                    .description("Permits currently held")
                    .register(registry);
        }
    }

    /**
     * Waits up to the configured timeout for a permit. Returns {@code false}
     * when the gate stayed saturated (including when the caller is interrupted,
     * in which case the interrupt flag is restored).
     */
    public boolean tryEnter() {
        long started = System.nanoTime();
        try {
            boolean acquired = permits.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            recordWait(started);
            if (!acquired) {
                reject();
                log.warn("Concurrency gate '{}' still saturated after {} ms ({} in flight)",
                        name, waitTimeout.toMillis(), inFlight());
            }
            return acquired;
        } catch (InterruptedException _) {
            recordWait(started);
            reject();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordWait(long started) {
        if (waitTimer != null) {
            waitTimer.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private void reject() {
        if (rejections != null) {
            rejections.increment();
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
