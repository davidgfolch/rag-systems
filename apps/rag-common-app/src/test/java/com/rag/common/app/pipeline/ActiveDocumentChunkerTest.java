package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveDocumentChunkerTest {

    private final VariantStore store = mock(VariantStore.class);
    private final DocumentChunker delegate = mock(DocumentChunker.class);
    private final ActiveDocumentChunker sut = new ActiveDocumentChunker(store);

    private static ParsedDocument document() {
        return new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of());
    }

    @Test
    void supportsWithTheActiveVariantChunker() {
        var document = document();
        when(store.chunker()).thenReturn(delegate);
        when(delegate.supports(document)).thenReturn(true);
        assertThat(sut.supports(document)).isTrue();
        verify(delegate).supports(document);
    }

    @Test
    void chunksWithTheActiveVariantChunker() {
        var document = document();
        var chunk = new Chunk("c1", "d1", "text", 0, Map.of());
        when(store.chunker()).thenReturn(delegate);
        when(delegate.chunk(document, ChunkingConfig.DEFAULT)).thenReturn(List.of(chunk));
        assertThat(sut.chunk(document, ChunkingConfig.DEFAULT)).containsExactly(chunk);
        verify(delegate).chunk(document, ChunkingConfig.DEFAULT);
    }
}
