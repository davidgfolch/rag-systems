package com.rag.basic.services;

import com.rag.common.domain.Chunk;
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
        log.debug("Retrieved {} chunks for query", results.size());
        return results;
    }
}