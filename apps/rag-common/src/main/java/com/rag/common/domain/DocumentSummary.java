package com.rag.common.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Aggregate view of a single ingested document, derived from the chunks stored
 * in a {@link com.rag.common.repositories.VectorStore}: how many chunks make it
 * up and a representative slice of its metadata (e.g. fileName/source/sourceType).
 */
public record DocumentSummary(String documentId, int chunkCount, Map<String, Object> metadata) {

    public DocumentSummary {
        Objects.requireNonNull(documentId, "documentId must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
