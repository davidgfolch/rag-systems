package com.rag.common.app.pipeline;

import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.SemanticChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.contract.pipeline.ChunkingStrategy;

import java.util.function.Supplier;

/** Builds the chunker that backs one pipeline variant. */
public final class Chunkers {

    public static final int CHARS_PER_TOKEN = 4;

    private Chunkers() {
    }

    /**
     * @param embeddings embedding source the semantic chunker cuts with; only
     *                   resolved for {@link ChunkingStrategy#SEMANTIC}, so the
     *                   other strategies never build an embedding client
     */
    public static TextSplitter of(ChunkingStrategy strategy, int size, int overlap,
                                  Supplier<EmbeddingModelPort> embeddings) {
        return switch (strategy) {
            case FIXED -> new FixedSizeChunker(size, overlap);
            case TOKEN -> new TokenChunker(size / CHARS_PER_TOKEN, overlap / CHARS_PER_TOKEN);
            case RECURSIVE -> new RecursiveCharacterChunker(size, overlap);
            case SEMANTIC -> new SemanticChunker(embeddings.get(), size, overlap);
        };
    }
}
