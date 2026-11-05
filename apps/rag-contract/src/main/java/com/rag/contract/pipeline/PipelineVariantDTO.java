package com.rag.contract.pipeline;

/**
 * One runnable pipeline variant of a module: the chunking strategy and window it
 * uses plus the embedding model it targets. Each variant owns its own vector
 * table, so two variants can be compared without merging their chunks.
 */
public record PipelineVariantDTO(
        String variantId,
        String module,
        String strategy,
        String embeddingModel,
        int embeddingDimension,
        int chunkSize,
        int chunkOverlap) {
}
