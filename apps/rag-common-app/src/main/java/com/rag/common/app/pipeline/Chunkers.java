package com.rag.common.app.pipeline;

import com.rag.common.core.services.TextSplitter;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.contract.pipeline.ChunkingStrategy;

/** Builds the chunker that backs one pipeline variant. */
public final class Chunkers {

    public static final int CHARS_PER_TOKEN = 4;

    private Chunkers() {
    }

    public static TextSplitter of(ChunkingStrategy strategy, int size, int overlap) {
        return switch (strategy) {
            case FIXED -> new FixedSizeChunker(size, overlap);
            case TOKEN -> new TokenChunker(size / CHARS_PER_TOKEN, overlap / CHARS_PER_TOKEN);
            case RECURSIVE, SEMANTIC -> new RecursiveCharacterChunker(size, overlap);
        };
    }
}
