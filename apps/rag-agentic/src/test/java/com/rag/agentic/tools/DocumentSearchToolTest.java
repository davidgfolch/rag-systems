package com.rag.agentic.tools;

import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.ToolCall;
import com.rag.common.core.repositories.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentSearchToolTest {

    private final VectorStorePort vectorStore = mock(VectorStorePort.class);
    private DocumentSearchTool sut;

    @BeforeEach
    void setUp() {
        sut = new DocumentSearchTool(vectorStore, 5);
    }

    @Test
    void searchesScopedToDocument() {
        when(vectorStore.similaritySearch("query", 5, "doc-1")).thenReturn(list("a", "b"));
        var result = sut.execute(new ToolCall("document_search", "query", "doc-1", "", 0));
        verify(vectorStore).similaritySearch("query", 5, "doc-1");
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.text()).contains("a", "b");
    }

    @Test
    void usesExplicitTopK() {
        when(vectorStore.similaritySearch("query", 9, "doc-1")).thenReturn(list("a"));
        sut.execute(new ToolCall("document_search", "query", "doc-1", "", 9));
        verify(vectorStore).similaritySearch("query", 9, "doc-1");
    }

    @Test
    void returnsEmptyWhenQueryMissing() {
        var result = sut.execute(new ToolCall("document_search", "", "doc-1", "", 5));
        assertThat(result.hasContent()).isFalse();
        verify(vectorStore, never()).similaritySearch(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsEmptyWhenDocumentIdMissing() {
        var result = sut.execute(new ToolCall("document_search", "query", "", "", 5));
        assertThat(result.chunks()).isEmpty();
    }

    @Test
    void defaultsTopKWhenConstructorValueIsZero() {
        var docTool = new DocumentSearchTool(vectorStore, 0);
        when(vectorStore.similaritySearch("query", AgenticConstants.DEFAULT_TOP_K, "doc-1"))
                .thenReturn(list("a"));
        docTool.execute(new ToolCall("document_search", "query", "doc-1", "", 0));
        verify(vectorStore).similaritySearch("query", AgenticConstants.DEFAULT_TOP_K, "doc-1");
    }
}