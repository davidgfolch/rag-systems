package com.rag.common.core.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Records retry attempts and applied backoff delays under a fixed component
 * tag. Operation names are deliberately not tagged: they contain variable
 * payload sizes (for example {@code embed 32 texts}) and would explode metric
 * cardinality.
 */
public final class MicrometerRetryObserver implements RetryObserver {

    public static final String RETRY_METRIC = "rag.resilience.retry";
    public static final String RETRY_DELAY_METRIC = "rag.resilience.retry.delay";
    static final String COMPONENT_TAG = "component";

    private final Counter retries;
    private final Timer delays;

    public MicrometerRetryObserver(MeterRegistry registry, String component) {
        this.retries = Counter.builder(RETRY_METRIC)
                .tag(COMPONENT_TAG, component)
                .description("Transient failures scheduled for another attempt")
                .register(registry);
        this.delays = Timer.builder(RETRY_DELAY_METRIC)
                .tag(COMPONENT_TAG, component)
                .description("Backoff delay applied before a retry")
                .register(registry);
    }

    @Override
    public void onRetry(String operation, int failedAttempt, int maxAttempts, Duration delay, Throwable failure) {
        retries.increment();
        delays.record(delay);
    }
}
