package com.rag.contract.provider;

/**
 * Identifies a provider profile and the model to use against it.
 */
public record ModelSpecDTO(String providerId, String model) {
}