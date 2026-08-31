package com.rag.tui.vectorstore;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgVectorStoreAdapterTest {

    private final org.springframework.ai.vectorstore.VectorStore delegate =
            mock(org.springframework.ai.vectorstore.VectorStore.class);

    private final VectorStore sut = new PgVectorStoreAdapter(delegate);

    @Test
    void mapsSpringDocumentBackToChunk() {
        org.springframework.ai.document.Document doc = org.springframework.ai.document.Document.builder()
                .id("c1")
                .text("retrieved text")
                .metadata(Map.of("documentId", "d1", "chunkIndex", 2))
                .build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Chunk> results = sut.similaritySearch("query", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("c1");
        assertThat(results.get(0).getDocumentId()).isEqualTo("d1");
        assertThat(results.get(0).getIndex()).isEqualTo(2);
        assertThat(results.get(0).getContent()).isEqualTo("retrieved text");
    }
}
