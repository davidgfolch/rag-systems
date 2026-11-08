package com.rag.common.core.resilience;

import java.time.Duration;

/**
 * Optional instrumentation hook invoked right before a retry is scheduled.
 * Implementations must be thread-safe because a single observer is shared by
 * every worker that retries the same operation.
 */
@FunctionalInterface
public interface RetryObserver {

    RetryObserver NOOP = (operation, failedAttempt, maxAttempts, delay, failure) -> { };

    void onRetry(String operation, int failedAttempt, int maxAttempts, Duration delay, Throwable failure);
}
