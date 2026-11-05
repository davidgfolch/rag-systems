package com.rag.common.app.pipeline;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.contract.pipeline.PipelineVariantDTO;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Storage lifecycle for one pipeline variant: the store used while the variant
 * is active, and the cleanup run when the variant is removed.
 */
public interface VariantStores {

    VectorStorePort create(PipelineVariantDTO variant);

    /** The embedding model this variant writes and queries with. */
    EmbeddingModel embedding(PipelineVariantDTO variant);

    void drop(PipelineVariantDTO variant);
}
