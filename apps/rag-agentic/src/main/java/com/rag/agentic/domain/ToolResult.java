package com.rag.agentic.domain;

import com.rag.common.core.domain.Chunk;

import java.util.List;

/**
 * Result of executing a single agent tool: a condensed text snippet fed back to
 * the agent prompt plus the raw chunks that count as answer sources.
 */
public record ToolResult(String text, List<Chunk> chunks) {

    public ToolResult {
        text = text == null ? "" : text;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public boolean hasContent() {
        return !text.isBlank() || !chunks.isEmpty();
    }
}