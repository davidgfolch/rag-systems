package com.rag.contract.provider;

/**
 * One model as listed in the models.dev catalog for a provider.
 *
 * @param providerId   provider id (catalog key)
 * @param modelId      model id (AI SDK identifier)
 * @param name         display name
 * @param limits       context/output token limits
 * @param capabilities reasoning, tool-calling and structured-output flags
 * @param cost         input/output price per million tokens (USD)
 * @param status       availability status (alpha/beta/deprecated), null when stable
 */
public record ProviderModelDTO(String providerId, String modelId, String name,
                               ModelLimitsDTO limits, ModelCapabilitiesDTO capabilities,
                               ModelCostDTO cost, String status) {
}