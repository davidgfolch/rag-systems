package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.VectorStorePort;

import java.util.List;

/**
 * Module-wide vector store that resolves the active variant's store per call, so
 * ingestion and retrieval immediately follow a variant switch.
 */
public class ActiveVectorStore implements VectorStorePort {

    private final VariantStore store;

    public ActiveVectorStore(VariantStore store) {
        this.store = store;
    }

    @Override
    public void add(List<Chunk> chunks) {
        store.store().add(chunks);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        return store.store().similaritySearch(query, topK);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        return store.store().similaritySearch(query, topK, documentId);
    }

    @Override
    public List<DocumentSummary> listDocuments() {
        return store.store().listDocuments();
    }

    @Override
    public void delete(String documentId) {
        store.store().delete(documentId);
    }

    @Override
    public void checkAvailable() {
        store.store().checkAvailable();
    }
}
