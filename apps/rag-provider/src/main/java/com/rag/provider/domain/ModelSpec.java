package com.rag.provider.domain;

/**
 * Identifies the provider profile ({@code providerId}) and the concrete model
 * to use against it.
 */
public record ModelSpec(String providerId, String model) {

    public ModelSpec {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }
}