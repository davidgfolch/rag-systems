package com.rag.provider.domain;

/**
 * Connection settings for a single provider (credentials and endpoints needed
 * to build Spring AI clients against it).
 */
public record ProviderProfile(String id, ProviderType type, String displayName,
                              String baseUrl, String apiKey) {

    public ProviderProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}