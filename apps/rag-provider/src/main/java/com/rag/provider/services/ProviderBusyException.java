package com.rag.provider.services;

/**
 * Raised when the provider is saturated and cannot admit another embedding
 * call within its configured queue wait. Mapped to HTTP 503 by the API layer so
 * callers can retry later instead of piling up in the provider.
 */
public class ProviderBusyException extends RuntimeException {

    public ProviderBusyException(String message) {
        super(message);
    }
}
