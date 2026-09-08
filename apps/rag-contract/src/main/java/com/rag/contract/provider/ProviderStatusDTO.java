package com.rag.contract.provider;

/**
 * Current routing state of the provider service.
 *
 * @param chat              active chat model spec
 * @param embedding         active embedding model spec
 * @param provider          id of the provider running chat
 * @param embeddingDimension dimensions of the active embedding model (-1 if unresolved)
 */
public record ProviderStatusDTO(ModelSpecDTO chat, ModelSpecDTO embedding,
                                String provider, int embeddingDimension) {
}