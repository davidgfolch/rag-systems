package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
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

    @Test
    void shouldNotifyTheObserverBeforeEveryRetry() {
        var observed = new ArrayList<String>();
        RetryObserver observer = (operation, attempt, max, delay, failure) ->
                observed.add(operation + ":" + attempt + "/" + max);
        var executor = new RetryExecutor(POLICY, error -> true, observer, delay -> { });

        assertThatThrownBy(() -> executor.run("op", () -> {
            throw new IllegalStateException("still failing");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(observed).containsExactly("op:1/4", "op:2/4", "op:3/4");
    }

    @Test
    void shouldHonourAServerRetryDelayThatExceedsTheComputedBackoff() {
        var sleeps = new ArrayList<Duration>();
        var policy = new RetryPolicy(2, Duration.ofMillis(1), Duration.ofSeconds(5), 2.0, 0);
        var executor = new RetryExecutor(policy, error -> true, sleeps::add);
        var attempts = new AtomicInteger();

        var result = executor.call("op", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HintedFailure(Duration.ofMillis(300));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).containsExactly(Duration.ofMillis(300));
    }

    @Test
    void shouldClampAServerRetryDelayToTheMaximumBackoff() {
        var sleeps = new ArrayList<Duration>();
        var policy = new RetryPolicy(2, Duration.ofMillis(1), Duration.ofSeconds(2), 2.0, 0);
        var executor = new RetryExecutor(policy, error -> true, sleeps::add);
        var attempts = new AtomicInteger();

        executor.call("op", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HintedFailure(Duration.ofMinutes(5));
            }
            return "ok";
        });

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void shouldIgnoreAServerRetryDelayShorterThanTheComputedBackoff() {
        var sleeps = new ArrayList<Duration>();
        var policy = new RetryPolicy(2, Duration.ofSeconds(1), Duration.ofSeconds(5), 2.0, 0);
        var executor = new RetryExecutor(policy, error -> true, sleeps::add);
        var attempts = new AtomicInteger();

        executor.call("op", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HintedFailure(Duration.ZERO);
            }
            return "ok";
        });

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    private static final class HintedFailure extends RuntimeException implements RetryHint {
        private final Duration retryAfter;

        private HintedFailure(Duration retryAfter) {
            super("overloaded");
            this.retryAfter = retryAfter;
        }

        @Override
        public Optional<Duration> retryAfter() {
            return Optional.of(retryAfter);
        }
    }
}
