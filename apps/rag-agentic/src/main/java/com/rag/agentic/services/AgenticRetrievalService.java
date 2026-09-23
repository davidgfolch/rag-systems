package com.rag.agentic.services;

import com.rag.agentic.orchestration.AgentLoop;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.DocumentStorePort;
import com.rag.common.core.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Retrieval facade for the rag-agentic module. Plain retrieval runs the agent
 * loop in "collect" mode so even the contract {@code /api/query} endpoint
 * benefits from multi-step planning; document listing and deletion are
 * delegated to the vector store.
 */
public class AgenticRetrievalService implements DocumentStorePort {

    private static final Logger log = LoggerFactory.getLogger(AgenticRetrievalService.class);

    private final VectorStorePort vectorStore;
    private final AgentLoop loop;

    public AgenticRetrievalService(VectorStorePort vectorStore, AgentLoop loop) {
        this.vectorStore = vectorStore;
        this.loop = loop;
    }

    public List<Chunk> retrieve(String question, int topK) {
        var trace = loop.collect(question, topK);
        log.info("Agentic retrieval: sources={}, steps={}", trace.sources().size(), trace.steps().size());
        return trace.sources();
    }

    public List<DocumentSummary> listDocuments() {
        return vectorStore.listDocuments();
    }

    public void delete(String documentId) {
        vectorStore.delete(documentId);
        log.info("Deleted document {}", documentId);
    }
}