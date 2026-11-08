package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyLimiterTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(20);

    @Test
    void shouldAdmitWorkUpToTheLimitAndRejectTheNextCaller() {
        var limiter = new ConcurrencyLimiter("test", 1, SHORT_WAIT);

        assertThat(limiter.tryEnter()).isTrue();
        assertThat(limiter.inFlight()).isEqualTo(1);

        assertThat(limiter.tryEnter()).isFalse();

        limiter.exit();
        assertThat(limiter.inFlight()).isZero();
        assertThat(limiter.tryEnter()).isTrue();
    }

    @Test
    void shouldRejectWhenWaitTimeoutExpires() {
        var limiter = new ConcurrencyLimiter("test", 1, SHORT_WAIT);
        limiter.tryEnter();

        var started = System.nanoTime();
        assertThat(limiter.tryEnter()).isFalse();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(SHORT_WAIT);
    }

    @Test
    void shouldRestoreInterruptFlagWhenWaitingIsInterrupted() {
        var limiter = new ConcurrencyLimiter("test", 1, Duration.ofSeconds(5));
        limiter.tryEnter();
        try {
            Thread.currentThread().interrupt();

            assertThat(limiter.tryEnter()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ConcurrencyLimiter("test", 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
