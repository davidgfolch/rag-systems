package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryHint;
import com.rag.common.core.resilience.RetryableStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Non-2xx response from rag-provider. Carries the status so the retry
 * classifier can distinguish transient overload from a client error, plus the
 * optional {@code Retry-After} delay the provider asked callers to honour.
 */
public class ProviderHttpException extends IllegalStateException implements RetryHint {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpException.class);

    private final int statusCode;
    private final Duration retryAfter;

    public ProviderHttpException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public ProviderHttpException(int statusCode, String message, Duration retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        log.debug("Provider call failed with HTTP {}: {}", statusCode, message);
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isRetryableStatus() {
        return RetryableStatuses.isRetryable(statusCode);
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
