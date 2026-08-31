package com.rag.basic.chunking;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveCharacterChunkerTest {

    private final RecursiveCharacterChunker chunker = new RecursiveCharacterChunker(40, 8);

    @Test
    void splitsByParagraphsThenSentences() {
        Document doc = new Document("d1",
                "First paragraph has some content that might be long enough.\n\n"
                        + "Second paragraph with more content.\n\nThird paragraph here.",
                Map.of());

        List<com.rag.common.domain.Chunk> chunks = chunker.split(doc);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata()).containsEntry("strategy", "recursive");
    }

    @Test
    void returnsEmptyForBlankContent() {
        Document doc = new Document("d1", " ", Map.of());
        assertThat(chunker.split(doc)).isEmpty();
    }

    @Test
    void keepsSmallTextIntact() {
        Document doc = new Document("d1", "Hello world", Map.of());
        List<com.rag.common.domain.Chunk> chunks = chunker.split(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("Hello world");
    }

    @Test
    void rejectsInvalidMaxSize() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new RecursiveCharacterChunker(0, 0));
    }
}