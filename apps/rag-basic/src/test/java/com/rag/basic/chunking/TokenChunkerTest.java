package com.rag.basic.chunking;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenChunkerTest {

    private final TokenChunker chunker = new TokenChunker(5, 1);

    @Test
    void splitsByTokenCount() {
        Document doc = new Document("d1",
                "one two three four five six seven eight nine ten eleven", Map.of());

        var chunks = chunker.split(doc);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getMetadata()).containsEntry("strategy", "token");
    }

    @Test
    void returnsEmptyForBlankContent() {
        Document doc = new Document("d1", "", Map.of());
        assertThat(chunker.split(doc)).isEmpty();
    }

    @Test
    void singleChunkForSmallText() {
        Document doc = new Document("d1", "one two three", Map.of());
        assertThat(chunker.split(doc)).hasSize(1);
    }

    @Test
    void rejectsInvalidMaxTokens() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new TokenChunker(0, 0));
    }
}