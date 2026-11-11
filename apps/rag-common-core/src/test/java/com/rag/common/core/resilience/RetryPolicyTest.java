package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void shouldExposeDefaultsWithRetries() {
        var policy = RetryPolicy.defaults();

        assertThat(policy.maxAttempts()).isEqualTo(RetryPolicy.DEFAULT_MAX_ATTEMPTS);
        assertThat(policy.maxAttempts()).isGreaterThan(1);
    }

    @Test
    void shouldDisableRetriesWithNoRetry() {
        var policy = RetryPolicy.noRetry();

        assertThat(policy.maxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldGrowExponentiallyWithoutJitter() {
        var policy = new RetryPolicy(4, Duration.ofMillis(100), Duration.ofSeconds(1), 2.0, 0);

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.nextDelay(4)).isEqualTo(Duration.ofMillis(800));
    }

    @Test
    void shouldCapTheDelayAtMaxBackoff() {
        var policy = new RetryPolicy(6, Duration.ofMillis(100), Duration.ofMillis(250), 2.0, 0);

        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.nextDelay(9)).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void shouldKeepJitteredDelayWithinBounds() {
        var policy = new RetryPolicy(4, Duration.ofMillis(200), Duration.ofSeconds(5), 2.0, 0.5);

        for (int attempt = 1; attempt <= 4; attempt++) {
            var delay = policy.nextDelay(attempt).toMillis();
            assertThat(delay).isBetween(100L, 200L << (attempt - 1));
        }
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        var shortBackoff = Duration.ofMillis(1);
        var longBackoff = Duration.ofMillis(10);
        assertThatThrownBy(() -> new RetryPolicy(0, shortBackoff, shortBackoff, 2.0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(2, longBackoff, shortBackoff, 2.0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(2, shortBackoff, longBackoff, 0.5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(2, shortBackoff, longBackoff, 2.0, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
