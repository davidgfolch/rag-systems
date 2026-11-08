package com.rag.common.generation.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport-level provider failure (timeout, connection reset, interrupted).
 * Retried only when the caller was not interrupted.
 */
final class ProviderTransportException extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(ProviderTransportException.class);

    private final boolean interrupted;

    private ProviderTransportException(Throwable cause, boolean interrupted) {
        super(cause.getMessage(), cause);
        this.interrupted = interrupted;
        log.debug("Provider transport failure (interrupted={}): {}", interrupted, cause.getMessage());
    }

    static ProviderTransportException failed(Throwable cause) {
        return new ProviderTransportException(cause, false);
    }

    static ProviderTransportException interrupted(Throwable cause) {
        return new ProviderTransportException(cause, true);
    }

    boolean isInterrupted() {
        return interrupted;
    }
}
