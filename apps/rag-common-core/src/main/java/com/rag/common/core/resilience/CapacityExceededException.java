package com.rag.common.core.resilience;

/**
 * Raised when a bounded worker pool cannot accept more work within the
 * configured wait timeout, so the work is never registered. Callers translate
 * this into their own protocol signal (HTTP 503, a skipped crawl page, etc.).
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }

    public CapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
