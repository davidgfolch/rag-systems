package com.rag.common.core.resilience;

import java.time.Duration;
import java.util.Optional;

/**
 * Failure that carries a server-provided minimum delay before the next attempt
 * (for example an HTTP {@code Retry-After} header). {@link RetryExecutor}
 * honours the hint when it is longer than the computed backoff, clamped to
 * {@link RetryPolicy#maxBackoff()}.
 */
@FunctionalInterface
public interface RetryHint {

    /** Requested wait before retrying, or empty when the failure carries none. */
    Optional<Duration> retryAfter();
}
