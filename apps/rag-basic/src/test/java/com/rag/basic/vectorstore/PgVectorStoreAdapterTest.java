package com.rag.basic.vectorstore;

import com.rag.common.domain.Chunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreAdapterTest {

    private final org.springframework.ai.vectorstore.VectorStore delegate =
            mock(org.springframework.ai.vectorstore.VectorStore.class);
    private final PgVectorStoreAdapter adapter = new PgVectorStoreAdapter(delegate);

    @Test
    void addsChunksAsSpringDocuments() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "test");
        Chunk chunk = new Chunk("c1", "d1", "content", 3, meta);

        adapter.add(List.of(chunk));

        verify(delegate).add(any());
    }

    @Test
    void convertsSearchResultsToChunks() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentId", "d1");
        meta.put("chunkIndex", 2);
        Document springDoc = new Document.Builder()
                .id("c1")
                .text("retrieved content")
                .metadata(meta)
                .build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        Chunk c = result.get(0);
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getDocumentId()).isEqualTo("d1");
        assertThat(c.getIndex()).isEqualTo(2);
        assertThat(c.getContent()).isEqualTo("retrieved content");
    }

    @Test
    void handlesMissingMetadataOnReturnedChunk() {
        Document springDoc = new Document.Builder().id("c1").text("text").build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentId()).isEqualTo("unknown");
        assertThat(result.get(0).getIndex()).isZero();
    }
}