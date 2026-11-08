package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderHttpSettingsTest {

    @Test
    void shouldExposeDefaultsWithHistoricalTimeoutsAndNoRetry() {
        var settings = ProviderHttpSettings.defaults();

        assertThat(settings.connectTimeout()).isEqualTo(ProviderHttpSettings.DEFAULT_CONNECT_TIMEOUT);
        assertThat(settings.requestTimeout()).isEqualTo(ProviderHttpSettings.DEFAULT_REQUEST_TIMEOUT);
        assertThat(settings.retryPolicy().retries()).isFalse();
    }

    @Test
    void shouldCarryTheProvidedRetryPolicy() {
        var policy = new RetryPolicy(5, Duration.ofMillis(10), Duration.ofMillis(100), 2.0, 0.5);
        var settings = new ProviderHttpSettings(Duration.ofSeconds(1), Duration.ofSeconds(2), policy);

        assertThat(settings.retryPolicy()).isSameAs(policy);
    }

    @Test
    void shouldRejectNonPositiveTimeouts() {
        var policy = RetryPolicy.noRetry();

        assertThatThrownBy(() -> new ProviderHttpSettings(Duration.ZERO, Duration.ofSeconds(1), policy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");
        assertThatThrownBy(() -> new ProviderHttpSettings(Duration.ofSeconds(1), Duration.ZERO, policy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestTimeout");
    }

    @Test
    void shouldRejectMissingRetryPolicy() {
        assertThatThrownBy(() -> new ProviderHttpSettings(Duration.ofSeconds(1), Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryPolicy");
    }
}
