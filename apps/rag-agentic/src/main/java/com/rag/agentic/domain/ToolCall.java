package com.rag.agentic.domain;

/**
 * A single tool invocation requested by the agent (or selected by the
 * deterministic fallback). {@link #topK()} is bounded deliberately: zero means
 * "use the caller's default".
 */
public record ToolCall(String name, String query, String documentId, String url, int topK) {

    public ToolCall {
        name = name == null ? "" : name;
        query = query == null ? "" : query;
        documentId = documentId == null ? "" : documentId;
        url = url == null ? "" : url;
    }

    public boolean hasQuery() {
        return !query.isBlank();
    }

    public boolean hasDocumentId() {
        return !documentId.isBlank();
    }

    public boolean hasUrl() {
        return !url.isBlank();
    }
}