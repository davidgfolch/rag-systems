package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.domain.MetadataKeys;
import com.rag.common.repositories.VectorStorePort;
import com.rag.common.services.IngestionService.EmptyExtractionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        when(embeddingModel.embed("content")).thenReturn(List.of(1.0f, 0.0f));

        var result = service.ingest(doc);

        assertThat(result.documentId()).isEqualTo("d1");
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(vectorStore).add(any());
    }

    @Test
    void embedsEachChunk() {
        var doc = new Document("d1", "content", Map.of());
        when(parser.parse(doc)).thenReturn("content");
        var c1 = new Chunk("c1", "d1", "part one", 0, Map.of());
        var c2 = new Chunk("c2", "d1", "part two", 1, Map.of());
        when(splitter.split(doc)).thenReturn(List.of(c1, c2));

        service.ingest(doc);

        verify(embeddingModel, times(2)).embed(any(String.class));
        assertThat(c1.getEmbedding()).isNotNull();
        assertThat(c2.getEmbedding()).isNotNull();
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