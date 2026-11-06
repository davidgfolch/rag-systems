package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatAwareChunkerDispatcherTest {

    private static final ChunkingConfig CONFIG = ChunkingConfig.DEFAULT;

    private static DocumentChunker chunker(SourceType type, String content) {
        return new DocumentChunker() {
            @Override
            public boolean supports(ParsedDocument document) {
                return document.sourceType() == type;
            }

            @Override
            public List<Chunk> chunk(ParsedDocument document, ChunkingConfig config) {
                return List.of(new Chunk(content, document.documentId(), content, 0, Map.of()));
            }
        };
    }

    private final FormatAwareChunkerDispatcher sut = new FormatAwareChunkerDispatcher(
            List.of(chunker(SourceType.HTML, "html"), chunker(SourceType.JSON, "json")));

    @Test
    void supportsAnyFormatThatAChildChunkerSupports() {
        assertThat(sut.supports(new ParsedDocument("d1", SourceType.HTML, Map.of(), List.of()))).isTrue();
        assertThat(sut.supports(new ParsedDocument("d1", SourceType.PDF, Map.of(), List.of()))).isFalse();
    }

    @Test
    void dispatchesToTheChunkerForTheDocumentFormat() {
        var json = new ParsedDocument("d1", SourceType.JSON, Map.of(), List.of());
        assertThat(sut.chunk(json, CONFIG)).extracting(Chunk::getContent).containsExactly("json");
    }

    @Test
    void failsLoudlyWhenNoChunkerMatches() {
        var pdf = new ParsedDocument("d1", SourceType.PDF, Map.of(), List.of());
        assertThatThrownBy(() -> sut.chunk(pdf, CONFIG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No chunker supports format PDF");
    }

    @Test
    void rejectsAnEmptyChunkerList() {
        assertThatThrownBy(() -> new FormatAwareChunkerDispatcher(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one chunker is required");
    }
}
