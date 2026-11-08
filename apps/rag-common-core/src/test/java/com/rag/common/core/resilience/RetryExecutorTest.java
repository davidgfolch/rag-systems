package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExecutorTest {

    private static final RetryPolicy POLICY =
            new RetryPolicy(4, Duration.ofMillis(1), Duration.ofMillis(1), 2.0, 0);

    @Test
    void shouldReturnFirstSuccessfulResultWithoutSleeping() {
        var sleeps = new ArrayList<Duration>();
        var executor = new RetryExecutor(POLICY, error -> true, sleeps::add);

        String result = executor.call("op", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void shouldRetryUntilTheOperationSucceeds() {
        var attempts = new AtomicInteger();
        var sleeps = new ArrayList<Duration>();
        var executor = new RetryExecutor(POLICY, error -> true, sleeps::add);

        String result = executor.call("op", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void shouldNotRetryFailuresRejectedByTheClassifier() {
        var attempts = new AtomicInteger();
        var executor = new RetryExecutor(POLICY, error -> false, delay -> { });

        assertThatThrownBy(() -> executor.call("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("permanent");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldGiveUpAfterMaxAttempts() {
        var attempts = new AtomicInteger();
        var executor = new RetryExecutor(POLICY, error -> true, delay -> { });

        assertThatThrownBy(() -> executor.run("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("still failing");
        })).isInstanceOf(IllegalStateException.class).hasMessage("still failing");

        assertThat(attempts).hasValue(POLICY.maxAttempts());
    }

    @Test
    void shouldRestoreInterruptFlagAndRethrowWhenSleepIsInterrupted() {
        var failure = new IllegalStateException("transient");
        RetryExecutor.Sleeper interrupted = delay -> {
            throw new InterruptedException("stop");
        };
        var executor = new RetryExecutor(POLICY, error -> true, interrupted);
        try {
            assertThatThrownBy(() -> executor.run("op", () -> {
                throw failure;
            })).isSameAs(failure);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
