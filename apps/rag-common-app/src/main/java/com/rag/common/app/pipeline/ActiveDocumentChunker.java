package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;

import java.util.List;

/**
 * Module-wide chunker that resolves the active variant per call, so activating
 * a variant changes the structural chunking of the next ingestion without a
 * restart.
 */
public class ActiveDocumentChunker implements DocumentChunker {

    private final VariantStore store;

    public ActiveDocumentChunker(VariantStore store) {
        this.store = store;
    }

    @Override
    public boolean supports(ParsedDocument document) {
        return store.chunker().supports(document);
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document, ChunkingConfig config) {
        return store.chunker().chunk(document, config);
    }
}
