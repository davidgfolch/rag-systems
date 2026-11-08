package com.rag.common.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs an operation with the retry/backoff schedule of a {@link RetryPolicy}.
 * Only failures accepted by the classifier are retried; everything else is
 * rethrown unchanged. Retries log at WARN so bursts stay visible.
 */
public final class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    private final RetryPolicy policy;
    private final Predicate<Throwable> retryable;
    private final Sleeper sleeper;

    public RetryExecutor(RetryPolicy policy, Predicate<Throwable> retryable) {
        this(policy, retryable, Thread::sleep);
    }

    RetryExecutor(RetryPolicy policy, Predicate<Throwable> retryable, Sleeper sleeper) {
        this.policy = policy;
        this.retryable = retryable;
        this.sleeper = sleeper;
    }

    public <T> T call(String operation, Supplier<T> action) {
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= policy.maxAttempts() || !retryable.test(e)) {
                    throw e;
                }
                backoff(operation, attempt, e);
            }
        }
        throw new IllegalStateException("Retry loop exhausted for " + operation);
    }

    public void run(String operation, Runnable action) {
        call(operation, () -> {
            action.run();
            return null;
        });
    }

    private void backoff(String operation, int failedAttempt, RuntimeException failure) {
        var delay = policy.nextDelay(failedAttempt);
        log.warn("{} failed (attempt {}/{}): {}; retrying in {} ms", operation, failedAttempt,
                policy.maxAttempts(), describe(failure), delay.toMillis());
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private static String describe(Throwable failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
