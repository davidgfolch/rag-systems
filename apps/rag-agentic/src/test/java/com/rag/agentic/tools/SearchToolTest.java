package com.rag.agentic.tools;

import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.ToolCall;
import com.rag.common.core.repositories.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchToolTest {

    private final VectorStorePort vectorStore = mock(VectorStorePort.class);
    private SearchTool sut;

    @BeforeEach
    void setUp() {
        sut = new SearchTool(vectorStore, 5);
    }

    @Test
    void searchesWithDefaultTopKWhenCallOmitsIt() {
        when(vectorStore.similaritySearch("query", 5)).thenReturn(list("alpha", "beta"));
        var result = sut.execute(new ToolCall("search", "query", "", "", 0));
        verify(vectorStore).similaritySearch("query", 5);
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.text()).contains("alpha", "beta");
        assertThat(result.hasContent()).isTrue();
    }

    @Test
    void usesTopKFromToolCallWhenProvided() {
        when(vectorStore.similaritySearch("query", 7)).thenReturn(list("alpha"));
        sut.execute(new ToolCall("search", "query", "", "", 7));
        verify(vectorStore).similaritySearch("query", 7);
    }

    @Test
    void returnsEmptyWhenQueryMissing() {
        var result = sut.execute(new ToolCall("search", "", "", "", 5));
        assertThat(result.chunks()).isEmpty();
        assertThat(result.hasContent()).isFalse();
    }

    @Test
    void joinsChunkContentsIntoSnippet() {
        when(vectorStore.similaritySearch("query", 5)).thenReturn(list("first chunk", "second chunk"));
        var result = sut.execute(new ToolCall("search", "query", "", "", 0));
        assertThat(result.text()).isEqualTo("first chunk\n---\nsecond chunk");
    }

    @Test
    void constructorDefaultsTopKWhenValueIsZero() {
        var fallback = new SearchTool(vectorStore, 0);
        when(vectorStore.similaritySearch("query", AgenticConstants.DEFAULT_TOP_K))
                .thenReturn(List.of());
        fallback.execute(new ToolCall("search", "query", "", "", 0));
        verify(vectorStore).similaritySearch("query", AgenticConstants.DEFAULT_TOP_K);
    }
}