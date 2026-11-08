package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryableStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-2xx response from rag-provider. Carries the status so the retry
 * classifier can distinguish transient overload from a client error.
 */
public class ProviderHttpException extends IllegalStateException {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpException.class);

    private final int statusCode;

    public ProviderHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        log.debug("Provider call failed with HTTP {}: {}", statusCode, message);
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isRetryableStatus() {
        return RetryableStatuses.isRetryable(statusCode);
    }
}
