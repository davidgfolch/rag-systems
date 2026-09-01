package com.rag.basic.services;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final RetrievalService service = new RetrievalService(vectorStore);

    @Test
    void delegatesQueryAndReturnsChunks() {
        Chunk result = new Chunk("c1", "d1", "content", 0, Map.of());
        when(vectorStore.similaritySearch("query", 5)).thenReturn(List.of(result));

        List<Chunk> chunks = service.retrieve("query", 5);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getId()).isEqualTo("c1");
    }

    @Test
    void returnsEmptyWhenNoMatches() {
        when(vectorStore.similaritySearch("query", 3)).thenReturn(List.of());

        assertThat(service.retrieve("query", 3)).isEmpty();
    }

    @Test
    void delegatesDocumentScopedQuery() {
        when(vectorStore.similaritySearch("query", 5, "d1")).thenReturn(List.of());

        assertThat(service.retrieve("query", 5, "d1")).isEmpty();
    }
}