package com.rag.agentic.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.agentic.agents.QueryPlanner;
import com.rag.agentic.agents.ReflectionAgent;
import com.rag.agentic.agents.ToolCallParser;
import com.rag.agentic.domain.ToolResult;
import com.rag.agentic.tools.AgentTool;
import com.rag.agentic.tools.SearchTool;
import com.rag.common.core.services.ChatModelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopTest {

    private static final ToolResult EMPTY = new ToolResult("", List.of());

    private final SearchTool searchTool = mock(SearchTool.class);
    private final AgentTool documentTool = mock(AgentTool.class);
    private final AgentTool webTool = mock(AgentTool.class);
    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private AgentLoop sut;

    @BeforeEach
    void setUp() {
        when(searchTool.name()).thenReturn("search");
        when(searchTool.execute(any())).thenReturn(new ToolResult("search results", list("a")));
        when(documentTool.name()).thenReturn("document_search");
        when(documentTool.execute(any())).thenReturn(EMPTY);
        when(webTool.name()).thenReturn("web_search");
        when(webTool.execute(any())).thenReturn(new ToolResult("web text", List.of()));
        var registry = new ToolRegistry(List.of(searchTool, documentTool, webTool), searchTool);
        sut = new AgentLoop(registry, new QueryPlanner(), new ReflectionAgent(),
                new ToolCallParser(new ObjectMapper()), chatModel, 3);
    }

    @Test
    void fallsBackToDeterministicSearchWhenModelOutputUnusable() {
        when(chatModel.complete(anyString())).thenReturn("");
        var trace = sut.collect("What is RAG?", 5);
        ArgumentCaptor<com.rag.agentic.domain.ToolCall> captor =
                ArgumentCaptor.forClass(com.rag.agentic.domain.ToolCall.class);
        verify(searchTool).execute(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("What is RAG");
        assertThat(trace.steps()).hasSize(1);
        assertThat(trace.steps().get(0).label()).isEqualTo("tool:search");
        assertThat(trace.hasSources()).isTrue();
    }

    @Test
    void executesModelSelectedToolCalls() {
        when(chatModel.complete(contains("retrieval agent"))).thenReturn("""
                [{"action":"tool","tool":"search","args":{"query":"What is RAG","topK":3}},
                 {"action":"tool","tool":"web_search","args":{"url":"https://x.io"}}]""");
        var trace = sut.collect("What is RAG?", 5);
        verify(searchTool).execute(any());
        verify(webTool).execute(any());
        assertThat(trace.steps()).hasSize(2);
    }

    @Test
    void resolvesUnknownToolToSearchFallback() {
        when(chatModel.complete(contains("retrieval agent"))).thenReturn(
                "[{\"action\":\"tool\",\"tool\":\"unknown\",\"args\":{\"query\":\"q\"}}]");
        var trace = sut.collect("plan then deploy", 5);
        verify(searchTool).execute(any());
        assertThat(trace.steps()).hasSize(1);
        assertThat(trace.steps().get(0).label()).isEqualTo("tool:search");
    }

    @Test
    void stopsWhenBudgetExhaustedWithoutContext() {
        when(chatModel.complete(anyString())).thenReturn("");
        when(searchTool.execute(any())).thenReturn(EMPTY);
        var loop = new AgentLoop(new ToolRegistry(List.of(searchTool), searchTool),
                new QueryPlanner(), new ReflectionAgent(), new ToolCallParser(new ObjectMapper()), chatModel, 2);
        var trace = loop.collect("What is RAG?", 5);
        verify(searchTool, times(2)).execute(any());
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.hasSources()).isFalse();
    }

    @Test
    void runsToFinalAnswer() {
        when(chatModel.complete(contains("retrieval agent"))).thenReturn("");
        when(chatModel.complete(contains("Answer:"))).thenReturn("Final answer");
        var trace = sut.run("What is RAG?", 5);
        assertThat(trace.answer()).isEqualTo("Final answer");
        assertThat(trace.steps()).hasSize(1);
    }

    @Test
    void generatesAnswerUsingGatheredContext() {
        when(chatModel.complete(contains("retrieval agent"))).thenReturn("");
        when(chatModel.complete(contains("Answer:"))).thenReturn("grounded");
        var trace = sut.run("finish, deploy", 5);
        assertThat(trace.answer()).isEqualTo("grounded");
        assertThat(trace.context()).isNotBlank();
    }
}