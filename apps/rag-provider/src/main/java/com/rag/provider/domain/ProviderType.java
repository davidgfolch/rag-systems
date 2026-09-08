package com.rag.provider.domain;

/**
 * Kind of provider backend the provider service can build clients for.
 */
public enum ProviderType {
    OLLAMA,
    OPENAI,
    OPENAI_COMPATIBLE
}