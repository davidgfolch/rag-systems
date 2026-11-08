package com.rag.common.core.resilience;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

/**
 * Creates resilience primitives bound to an optional {@link MeterRegistry}.
 * A missing registry (unit tests, modules without observability) yields the
 * same behaviour with metrics disabled instead of failing the wiring.
 */
public final class ResilienceMetrics {

    private final MeterRegistry registry;

    private ResilienceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public static ResilienceMetrics of(MeterRegistry registry) {
        return new ResilienceMetrics(registry);
    }

    public RetryObserver retryObserver(String component) {
        return registry == null ? RetryObserver.NOOP : new MicrometerRetryObserver(registry, component);
    }

    public ConcurrencyLimiter limiter(String name, int maxConcurrent, Duration waitTimeout) {
        return new ConcurrencyLimiter(name, maxConcurrent, waitTimeout, registry);
    }
}
