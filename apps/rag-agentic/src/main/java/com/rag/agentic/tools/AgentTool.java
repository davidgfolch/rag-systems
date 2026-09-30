package com.rag.agentic.tools;

import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.domain.ToolResult;

/**
 * Strategy interface for agent tools. Each tool owns one capability (vector
 * search, document-scoped search, web fetch) and returns a condensed snippet
 * plus the raw chunks that become answer sources. Tools are selected by name in
 * {@link com.rag.agentic.orchestration.ToolRegistry}.
 */
public interface AgentTool {

    String name();

    ToolResult execute(ToolCall call);
}