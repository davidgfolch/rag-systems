package com.rag.basic.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.repositories.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles retrieval: delegates the query to the vector store, which embeds
 * it internally (keeping this layer decoupled from the embedding strategy).
 */
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Chunk> retrieve(String query, int topK) {
        List<Chunk> results = vectorStore.similaritySearch(query, topK);
        log.info("Retrieved {} chunks for query", results.size());
        return results;
    }

    public List<Chunk> retrieve(String query, int topK, String documentId) {
        List<Chunk> results = vectorStore.similaritySearch(query, topK, documentId);
        log.info("Retrieved {} chunks for query scoped to {}", results.size(), documentId);
        return results;
    }

    public List<DocumentSummary> listDocuments() {
        List<DocumentSummary> summaries = vectorStore.listDocuments();
        log.debug("Listed {} documents", summaries.size());
        return summaries;
    }
}