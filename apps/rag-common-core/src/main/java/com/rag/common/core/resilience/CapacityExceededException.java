package com.rag.common.core.resilience;

/**
 * Raised when a bounded worker pool cannot accept more work within the
 * configured wait timeout. Callers translate this into their own protocol
 * signal (an HTTP 503, a failed ingestion job, etc.).
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }

    public CapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
