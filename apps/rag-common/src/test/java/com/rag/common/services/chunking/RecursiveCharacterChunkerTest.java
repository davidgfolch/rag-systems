package com.rag.common.services.chunking;

import com.rag.common.domain.Document;
import com.rag.common.domain.MetadataKeys;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecursiveCharacterChunkerTest {

    private final RecursiveCharacterChunker chunker = new RecursiveCharacterChunker(40, 8);

    @Test
    void splitsByParagraphsThenSentences() {
        Document doc = new Document("d1", """
                First paragraph has some content that might be long enough.

                Second paragraph with more content.

                Third paragraph here.""", Map.of());

        var chunks = chunker.split(doc);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata()).containsEntry(MetadataKeys.STRATEGY, "recursive");
    }

    @Test
    void returnsEmptyForBlankContent() {
        Document doc = new Document("d1", " ", Map.of());
        assertThat(chunker.split(doc)).isEmpty();
    }

    @Test
    void keepsSmallTextIntact() {
        Document doc = new Document("d1", "Hello world", Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("Hello world");
    }

    @Test
    void rejectsInvalidMaxSize() {
assertThrows(
        IllegalArgumentException.class, () -> new RecursiveCharacterChunker(0, 0));
    }

    @Test
    void splitsLongSingleWordByCharacter() {
        Document doc = new Document("d1", "A".repeat(100), Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).isNotEmpty();
        long total = chunks.stream()
                .mapToLong(c -> c.getContent().chars().filter(ch -> ch == 'A').count())
                .sum();
        assertThat(total).isGreaterThanOrEqualTo(100);
    }

    @Test
    void splitsOversizedLeadingPartWhenCurrentIsEmpty() {
        Document doc = new Document("d1", "B".repeat(80) + " and some normal words here", Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).isNotEmpty();
        long total = chunks.stream()
                .mapToLong(c -> c.getContent().chars().filter(ch -> ch == 'B').count())
                .sum();
        assertThat(total).isGreaterThanOrEqualTo(80);
    }

    @Test
    void splitsWhenLaterPartIsOversized() {
        Document doc = new Document("d1", "short part " + "L".repeat(80), Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).isNotEmpty();
        long total = chunks.stream()
                .mapToLong(c -> c.getContent().chars().filter(ch -> ch == 'L').count())
                .sum();
        assertThat(total).isGreaterThanOrEqualTo(80);
    }

    @Test
    void skipsBlankChunkFromTrailingSeparator() {
        Document doc = new Document("d1", "Sentence one. ", Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).noneSatisfy(
                c -> assertThat(c.getContent().trim()).isEmpty());
    }
}