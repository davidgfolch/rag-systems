package com.rag.common.core.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceMetricsTest {

    @Test
    void shouldReturnNoopObserverWithoutRegistry() {
        assertThat(ResilienceMetrics.of(null).retryObserver("unit")).isSameAs(RetryObserver.NOOP);
    }

    @Test
    void shouldCreateAnInstrumentedObserverWhenRegistryIsPresent() {
        var registry = new SimpleMeterRegistry();
        var observer = ResilienceMetrics.of(registry).retryObserver("unit");

        observer.onRetry("op", 1, 4, Duration.ofMillis(250), new IllegalStateException("transient"));

        assertThat(registry.get(MicrometerRetryObserver.RETRY_METRIC)
                .tag(MicrometerRetryObserver.COMPONENT_TAG, "unit")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MicrometerRetryObserver.RETRY_DELAY_METRIC)
                .tag(MicrometerRetryObserver.COMPONENT_TAG, "unit")
                .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
    }

    @Test
    void shouldCreateAnInstrumentedLimiterWhenRegistryIsPresent() {
        var registry = new SimpleMeterRegistry();
        var limiter = ResilienceMetrics.of(registry).limiter("gate", 1, Duration.ZERO);

        assertThat(limiter.tryEnter()).isTrue();
        limiter.exit();

        assertThat(registry.find(ConcurrencyLimiter.IN_FLIGHT_METRIC).gauge()).isNotNull();
    }
}
