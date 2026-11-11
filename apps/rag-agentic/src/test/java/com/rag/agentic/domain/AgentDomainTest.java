package com.rag.agentic.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;

class AgentDomainTest {

    @Test
    void toolCallNormalizesNullsAndExposesGuards() {
        var call = new ToolCall(null, "q", "", null, 3);
        assertThat(call.name()).isEmpty();
        assertThat(call.url()).isEmpty();
        assertThat(call.hasQuery()).isTrue();
        assertThat(call.hasDocumentId()).isFalse();
        assertThat(call.hasUrl()).isFalse();
    }

    @Test
    void toolResultDefaultsNullTextAndChunks() {
        var result = new ToolResult(null, List.of(list("a").get(0)));
        assertThat(result.text()).isEmpty();
        assertThat(result.chunks()).isNotEmpty();
        assertThat(new ToolResult("", null).chunks()).isEmpty();
    }

    @Test
    void agentStepRecordsPhaseAndDetail() {
        var step = new AgentStep("tool:search", "snippet");
        assertThat(step.label()).isEqualTo("tool:search");
        assertThat(step.detail()).isEqualTo("snippet");
    }

    @Test
    void agentTraceCopiesListsAndDefaultsContext() {
        var sources = list("a");
        var steps = List.of(new AgentStep("p", "d"));
        var trace = new AgentTrace(null, sources, steps, "");
        assertThat(trace.context()).isEmpty();
        assertThat(trace.sources()).isNotEmpty();
        assertThat(new AgentTrace(null, List.of(), List.of(), null).context()).isEmpty();
        assertThat(new AgentTrace("ans", List.of(), List.of(), "ctx").answer()).isEqualTo("ans");
    }

    @Test
    void constantsExposeToolNamesAndDefaults() {
        assertThat(AgenticConstants.TOOL_SEARCH).isEqualTo("search");
        assertThat(AgenticConstants.DEFAULT_MAX_STEPS).isPositive();
        assertThat(AgenticConstants.DEFAULT_TOP_K).isEqualTo(5);
        assertThat(AgenticConstants.UNSPECIFIED_TOP_K).isZero();
    }
}