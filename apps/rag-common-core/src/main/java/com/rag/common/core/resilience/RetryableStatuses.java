package com.rag.common.core.resilience;

/**
 * HTTP statuses that indicate a transient downstream condition and are safe to
 * retry for idempotent calls. Shared by the provider, crawler and webclient
 * adapters so the retry decision stays consistent.
 */
public final class RetryableStatuses {

    private static final int REQUEST_TIMEOUT = 408;
    private static final int TOO_EARLY = 425;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR_MIN = 500;
    private static final int SERVER_ERROR_MAX = 599;

    private RetryableStatuses() {
    }

    public static boolean isRetryable(int statusCode) {
        return statusCode == REQUEST_TIMEOUT || statusCode == TOO_EARLY
                || statusCode == TOO_MANY_REQUESTS
                || (statusCode >= SERVER_ERROR_MIN && statusCode <= SERVER_ERROR_MAX);
    }
}
