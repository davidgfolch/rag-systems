package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

/**
 * Classification for provider retries: timeouts and connection failures are
 * transient, as are 408/425/429 and 5xx responses. Client errors (bad model,
 * invalid key) and interrupts are not retried.
 */
final class ProviderHttpRetry {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpRetry.class);

    private ProviderHttpRetry() {
    }

    static RetryPolicy policy(ProviderHttpSettings settings) {
        var policy = settings.retryPolicy();
        log.debug("Provider calls allow {} attempt(s)", policy.maxAttempts());
        return policy;
    }

    static boolean isRetryable(Throwable failure) {
        if (failure instanceof ProviderTransportException transport) {
            return !transport.isInterrupted() && isTransportFailure(transport.getCause());
        }
        return failure instanceof ProviderHttpException http && http.isRetryableStatus();
    }

    private static boolean isTransportFailure(Throwable cause) {
        return cause instanceof HttpTimeoutException
                || cause instanceof ConnectException
                || cause instanceof IOException;
    }
}
