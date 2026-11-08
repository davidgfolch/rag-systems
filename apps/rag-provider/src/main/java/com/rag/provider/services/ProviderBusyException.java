package com.rag.provider.services;

import java.time.Duration;

/**
 * Raised when the provider is saturated and cannot admit another embedding
 * call within its configured queue wait. Mapped to HTTP 429 with a
 * {@code Retry-After} header so callers back off instead of piling up in the
 * provider.
 */
public class ProviderBusyException extends RuntimeException {

    public static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);

    private final Duration retryAfter;

    public ProviderBusyException(String message) {
        this(message, DEFAULT_RETRY_AFTER);
    }

    public ProviderBusyException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter == null || retryAfter.isNegative() ? DEFAULT_RETRY_AFTER : retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
