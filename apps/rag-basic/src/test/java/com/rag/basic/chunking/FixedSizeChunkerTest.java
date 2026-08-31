package com.rag.basic.chunking;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker(20, 4);

    @Test
    void splitsLongTextIntoFixedPieces() {
        Document doc = new Document("d1", "This is a fairly long piece of content that needs splitting.", Map.of());

        List<com.rag.common.domain.Chunk> chunks = chunker.split(doc);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getDocumentId()).isEqualTo("d1");
        assertThat(chunks.get(0).getMetadata()).containsEntry("strategy", "fixed");
    }

    @Test
    void returnsEmptyForBlankContent() {
        Document doc = new Document("d1", "   ", Map.of());
        assertThat(chunker.split(doc)).isEmpty();
    }

    @Test
    void returnsSingleChunkForShortText() {
        Document doc = new Document("d1", "Short text", Map.of());
        assertThat(chunker.split(doc)).hasSize(1);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new FixedSizeChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunker(10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}