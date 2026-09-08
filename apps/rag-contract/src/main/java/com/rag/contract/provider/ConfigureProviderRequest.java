package com.rag.contract.provider;

/**
 * Registers or updates a provider profile ({@code type} is one of
 * OLLAMA/OPENAI/OPENAI_COMPATIBLE).
 */
public record ConfigureProviderRequest(String providerId, String type, String displayName,
                                       String baseUrl, String apiKey) {
}