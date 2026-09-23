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
 * Default search tool: embeds the query and runs an un-scoped similarity search
 * over the active module vector store.
 */
public class SearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SearchTool.class);

    private final VectorStorePort vectorStore;
    private final int defaultTopK;

    public SearchTool(VectorStorePort vectorStore, int defaultTopK) {
        this.vectorStore = vectorStore;
        this.defaultTopK = topKOrDefault(defaultTopK);
    }

    @Override
    public String name() {
        return AgenticConstants.TOOL_SEARCH;
    }

    @Override
    public ToolResult execute(ToolCall call) {
        int topK = call.topK() > AgenticConstants.UNSPECIFIED_TOP_K ? call.topK() : defaultTopK;
        if (!call.hasQuery()) {
            log.warn("Search tool ignored: no query in tool call");
            return new ToolResult("", List.of());
        }
        var chunks = vectorStore.similaritySearch(call.query(), topK);
        log.info("Search tool returned {} chunks for query '{}'", chunks.size(), call.query());
        return new ToolResult(snippets(chunks), chunks);
    }

    private static int topKOrDefault(int value) {
        return value > 0 ? value : AgenticConstants.DEFAULT_TOP_K;
    }

    private static String snippets(List<Chunk> chunks) {
        return String.join("\n---\n", chunks.stream()
                .map(Chunk::getContent)
                .toList());
    }
}