package com.rag.tui.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.TextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private final DocumentParser parser = mock(DocumentParser.class);
    private final TextSplitter splitter = mock(TextSplitter.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final VectorStore vectorStore = mock(VectorStore.class);

    private final IngestionService sut =
            new IngestionService(parser, splitter, embeddingModel, vectorStore);

    @Test
    void ingestsDocumentEndToEnd() {
        Document doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        Chunk chunk = new Chunk("c1", "d1", "content", 0, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(chunk));
        when(embeddingModel.embed("content")).thenReturn(List.of(1.0f, 0.0f));

        IngestionService.IngestionResult result = sut.ingest(doc);

        assertThat(result.documentId()).isEqualTo("d1");
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(vectorStore).add(any());
    }

    @Test
    void embedsEachChunk() {
        Document doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        Chunk c1 = new Chunk("c1", "d1", "part one", 0, Map.of());
        Chunk c2 = new Chunk("c2", "d1", "part two", 1, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(c1, c2));

        sut.ingest(doc);

        verify(embeddingModel, times(2)).embed(any(String.class));
        assertThat(c1.getEmbedding()).isNotNull();
        assertThat(c2.getEmbedding()).isNotNull();
    }
}
