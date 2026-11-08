package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryPolicy;

import java.time.Duration;

/**
 * Timeouts and retry schedule for calls from a RAG module to rag-provider.
 */
public record ProviderHttpSettings(Duration connectTimeout, Duration requestTimeout,
                                   RetryPolicy retryPolicy) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public ProviderHttpSettings {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy must not be null");
        }
    }

    public static ProviderHttpSettings defaults() {
        return new ProviderHttpSettings(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT,
                RetryPolicy.noRetry());
    }

    public static ProviderHttpSettings resilient(Duration connectTimeout, Duration requestTimeout) {
        return new ProviderHttpSettings(connectTimeout, requestTimeout, RetryPolicy.defaults());
    }
}
