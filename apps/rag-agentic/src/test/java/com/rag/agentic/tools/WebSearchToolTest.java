package com.rag.agentic.tools;

import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.services.WebCrawlerClient;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSearchToolTest {

    private final WebCrawlerClient webCrawlerClient = mock(WebCrawlerClient.class);
    private final WebSearchTool sut = new WebSearchTool(webCrawlerClient);

    @Test
    void fetchesPageTextForUrl() {
        when(webCrawlerClient.fetch("https://example.com")).thenReturn(
                new PageDTO().url("https://example.com").title("T").text("live content"));
        var result = sut.execute(new ToolCall("web_search", "", "", "https://example.com", 0));
        verify(webCrawlerClient).fetch("https://example.com");
        assertThat(result.text()).isEqualTo("live content");
    }

    @Test
    void returnsEmptyWhenNoUrl() {
        var result = sut.execute(new ToolCall("web_search", "", "", "", 0));
        assertThat(result.hasContent()).isFalse();
    }
}