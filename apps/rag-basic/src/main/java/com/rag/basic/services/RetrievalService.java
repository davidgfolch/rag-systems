package com.rag.basic.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles retrieval: delegates the query to the vector store, which embeds
 * it internally (keeping this layer decoupled from the embedding strategy).
 */
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStorePort vectorStore;

    public RetrievalService(VectorStorePort vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Chunk> retrieve(String query, int topK) {
        var results = vectorStore.similaritySearch(query, topK);
        log.info("Retrieved {} chunks for query", results.size());
        return results;
    }

    public List<Chunk> retrieve(String query, int topK, String documentId) {
        var results = vectorStore.similaritySearch(query, topK, documentId);
        log.info("Retrieved {} chunks for query scoped to {}", results.size(), documentId);
        return results;
    }

    public List<DocumentSummary> listDocuments() {
        var summaries = vectorStore.listDocuments();
        log.debug("Listed {} documents", summaries.size());
        return summaries;
    }
}