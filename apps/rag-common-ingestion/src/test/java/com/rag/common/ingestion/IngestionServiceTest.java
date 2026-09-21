package com.rag.common.ingestion;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.MetadataKeys;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.DocumentParser;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.ingestion.IngestionService.EmptyExtractionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private final DocumentParser parser = mock(DocumentParser.class);
    private final TextSplitter splitter = mock(TextSplitter.class);
    private final EmbeddingModelPort embeddingModel = mock(EmbeddingModelPort.class);
    private final VectorStorePort vectorStore = mock(VectorStorePort.class);

    private final IngestionService service =
            new IngestionService(parser, splitter, embeddingModel, vectorStore);

    @Test
    void ingestsDocumentEndToEnd() {
        var doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        var chunk = new Chunk("c1", "d1", "content", 0, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(chunk));
        when(embeddingModel.embed(List.of("content"))).thenReturn(List.of(List.of(1.0f, 0.0f)));
        var result = service.ingest(doc);
        assertThat(result.documentId()).isEqualTo("d1");
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(vectorStore).add(any());
    }

    @Test
    void embedsAllChunksInSingleCallWhenBelowBatchSize() {
        var doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        var c1 = new Chunk("c1", "d1", "part one", 0, Map.of());
        var c2 = new Chunk("c2", "d1", "part two", 1, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(c1, c2));
        when(embeddingModel.embed(List.of("part one", "part two")))
                .thenReturn(List.of(List.of(1.0f, 0.0f), List.of(0.0f, 1.0f)));
        service.ingest(doc);
        verify(embeddingModel).embed(List.of("part one", "part two"));
        assertThat(c1.getEmbedding()).containsExactly(1.0f, 0.0f);
        assertThat(c2.getEmbedding()).containsExactly(0.0f, 1.0f);
    }

    @Test
    void embedsChunksInBoundedWindowsWhenAboveBatchSize() {
        var batchService = new IngestionService(parser, splitter, embeddingModel, vectorStore, 2);
        var doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        var c1 = new Chunk("c1", "d1", "part one", 0, Map.of());
        var c2 = new Chunk("c2", "d1", "part two", 1, Map.of());
        var c3 = new Chunk("c3", "d1", "part three", 2, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(c1, c2, c3));
        when(embeddingModel.embed(List.of("part one", "part two")))
                .thenReturn(List.of(List.of(1.0f, 0.0f), List.of(0.0f, 1.0f)));
        when(embeddingModel.embed(List.of("part three")))
                .thenReturn(List.of(List.of(2.0f, 2.0f)));
        batchService.ingest(doc);
        verify(embeddingModel).embed(List.of("part one", "part two"));
        verify(embeddingModel).embed(List.of("part three"));
        assertThat(c1.getEmbedding()).containsExactly(1.0f, 0.0f);
        assertThat(c2.getEmbedding()).containsExactly(0.0f, 1.0f);
        assertThat(c3.getEmbedding()).containsExactly(2.0f, 2.0f);
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new IngestionService(parser, splitter, embeddingModel, vectorStore, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding batch size must be positive");
    }

    @Test
    void rejectsAnyDocumentThatYieldsNoText() {
        var doc = new Document("d1", "", Map.of());
        when(parser.parse(doc)).thenReturn("");
        assertThatThrownBy(() -> service.ingest(doc))
                .isInstanceOf(EmptyExtractionException.class)
                .hasMessageContaining(EmptyExtractionException.MSG);
    }

    @Test
    void rejectsBinaryDocumentThatYieldsNoText() {
        var doc = new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, new byte[]{0x25, 0x50, 0x44, 0x46}));
        when(parser.parse(doc)).thenReturn("");
        assertThatThrownBy(() -> service.ingest(doc))
                .isInstanceOf(EmptyExtractionException.class)
                .hasMessageContaining(EmptyExtractionException.MSG);
    }
}