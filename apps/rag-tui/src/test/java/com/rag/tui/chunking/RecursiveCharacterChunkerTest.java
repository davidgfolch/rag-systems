package com.rag.tui.chunking;

import com.rag.common.domain.Document;
import com.rag.common.services.TextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecursiveCharacterChunkerTest {

    private final TextSplitter sut = new RecursiveCharacterChunker(20, 4);

    @Test
    void returnsSingleChunkForShortContent() {
        List<com.rag.common.domain.Chunk> chunks = sut.split(new Document("d1", "short text", Map.of()));
        assertThat(chunks).hasSize(1);
    }

    @Test
    void splitsLongContentAcrossBoundaries() {
        String content = "First paragraph content here.\n\nSecond paragraph with more words.";
        List<com.rag.common.domain.Chunk> chunks = sut.split(new Document("d1", content, Map.of()));
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).getDocumentId()).isEqualTo("d1");
        assertThat(chunks.get(0).getMetadata()).containsKey("strategy");
    }

    @Test
    void returnsEmptyForBlankContent() {
        List<com.rag.common.domain.Chunk> chunks = sut.split(new Document("d1", "   ", Map.of()));
        assertThat(chunks).isEmpty();
    }

    @Test
    void rejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> new RecursiveCharacterChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
