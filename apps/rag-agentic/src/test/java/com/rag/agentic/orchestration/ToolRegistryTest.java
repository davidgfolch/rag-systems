package com.rag.agentic.orchestration;

import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.tools.AgentTool;
import com.rag.agentic.tools.SearchTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryTest {

    private final AgentTool search = namedTool("search");
    private final SearchTool fallback = mock(SearchTool.class);
    private final ToolRegistry sut = new ToolRegistry(List.of(search, namedTool("web_search")), fallback);

    private static AgentTool namedTool(String name) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        return tool;
    }

    @Test
    void findsRegisteredToolByName() {
        assertThat(sut.find("search")).contains(search);
        assertThat(sut.find("web_search")).isPresent();
    }

    @Test
    void returnsEmptyWhenNameUnknown() {
        assertThat(sut.find("nope")).isEmpty();
    }

    @Test
    void resolvesUnknownNameToFallback() {
        assertThat(sut.resolve("nope")).isSameAs(fallback);
    }

    @Test
    void resolvesCallByName() {
        assertThat(sut.resolve(new ToolCall("search", "q", "", "", 3))).isSameAs(search);
    }

    @Test
    void exposesFallbackAndNames() {
        assertThat(sut.fallback()).isSameAs(fallback);
        assertThat(sut.names()).contains("search", "web_search");
    }
}