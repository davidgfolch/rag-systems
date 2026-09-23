package com.rag.agentic.orchestration;

import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.tools.AgentTool;
import com.rag.agentic.tools.SearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Name -> tool map for agent dispatch. Unknown names resolve to the provided
 * search fallback so a hallucinated tool name degrades to a plain retrieval
 * instead of failing the loop.
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> byName;
    private final SearchTool fallback;

    public ToolRegistry(List<AgentTool> tools, SearchTool fallback) {
        this.byName = tools.stream().collect(Collectors.toUnmodifiableMap(AgentTool::name, t -> t));
        this.fallback = fallback;
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public AgentTool resolve(String name) {
        return find(name).orElseGet(() -> {
            log.warn("Unknown tool '{}', falling back to search", name);
            return fallback;
        });
    }

    public AgentTool resolve(ToolCall call) {
        return resolve(call.name());
    }

    public AgentTool fallback() {
        return fallback;
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }
}