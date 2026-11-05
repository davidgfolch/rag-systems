package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.services.TextSplitter;

import java.util.List;

/**
 * Module-wide splitter that resolves the active variant per call, so activating
 * a variant changes the chunking of the next ingestion without a restart.
 */
public class ActiveTextSplitter implements TextSplitter {

    private final VariantStore store;

    public ActiveTextSplitter(VariantStore store) {
        this.store = store;
    }

    @Override
    public List<Chunk> split(Document document) {
        return store.splitter().split(document);
    }
}
