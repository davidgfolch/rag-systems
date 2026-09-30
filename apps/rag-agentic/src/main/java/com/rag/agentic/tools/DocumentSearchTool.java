package com.rag.agentic.tools;

import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.domain.ToolResult;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Document-scoped search tool: restricts the similarity search to a single
 * ingested document via its {@code documentId} chunk metadata (see ADR-0006).
 */
public class DocumentSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchTool.class);

    private final VectorStorePort vectorStore;
    private final int defaultTopK;

    public DocumentSearchTool(VectorStorePort vectorStore, int defaultTopK) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK > 0 ? defaultTopK : AgenticConstants.DEFAULT_TOP_K;
    }

    @Override
    public String name() {
        return AgenticConstants.TOOL_DOCUMENT_SEARCH;
    }

    @Override
    public ToolResult execute(ToolCall call) {
        int topK = call.topK() > AgenticConstants.UNSPECIFIED_TOP_K ? call.topK() : defaultTopK;
        if (!call.hasQuery() || !call.hasDocumentId()) {
            log.warn("Document search tool ignored: query={}, documentId={}", call.query(), call.documentId());
            return new ToolResult("", List.of());
        }
        var chunks = vectorStore.similaritySearch(call.query(), topK, call.documentId());
        log.info("Document search tool returned {} chunks for document {}", chunks.size(), call.documentId());
        return new ToolResult(snippets(chunks), chunks);
    }

    private static String snippets(List<Chunk> chunks) {
        return String.join("\n---\n", chunks.stream()
                .map(Chunk::getContent)
                .toList());
    }
}