package com.rag.agentic.tools;

import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.domain.ToolResult;
import com.rag.agentic.services.WebCrawlerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Web search tool: fetches a URL through the shared rag-webcrawler service. The
 * page text extends the agent's context beyond the vector store, so the agent
 * can ground answers on live web content.
 */
public class WebSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private final WebCrawlerClient webCrawlerClient;

    public WebSearchTool(WebCrawlerClient webCrawlerClient) {
        this.webCrawlerClient = webCrawlerClient;
    }

    @Override
    public String name() {
        return AgenticConstants.TOOL_WEB_SEARCH;
    }

    @Override
    public ToolResult execute(ToolCall call) {
        if (!call.hasUrl()) {
            log.warn("Web search tool ignored: no url in tool call");
            return new ToolResult("", List.of());
        }
        var page = webCrawlerClient.fetch(call.url());
        log.info("Web search tool fetched {} ({} chars)", page.getUrl(), page.getText().length());
        return new ToolResult(page.getText(), List.of());
    }
}