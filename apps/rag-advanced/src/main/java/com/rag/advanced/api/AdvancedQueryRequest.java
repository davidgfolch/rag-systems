package com.rag.advanced.api;

import java.util.Map;

/**
 * Module-specific retrieval query with an optional metadata filter. The shared
 * contract only exposes question/topK/documentId; the metadata filter is a
 * rag-advanced strategy, so it lives here instead of in rag-contract.
 */
public record AdvancedQueryRequest(String question, Integer topK, String documentId, Map<String, Object> metadata) {

    public AdvancedQueryRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}