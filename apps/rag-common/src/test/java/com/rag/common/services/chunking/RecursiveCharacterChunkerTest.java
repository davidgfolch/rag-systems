package com.rag.common.services.chunking;

import com.rag.common.domain.Document;
import com.rag.common.domain.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecursiveCharacterChunkerTest {

    private final RecursiveCharacterChunker chunker = new RecursiveCharacterChunker(40, 8);

    @ParameterizedTest(name = "input=\"{0}\" → {1}")
    @MethodSource("splitInputProvider")
    void splitReturnsExpectedChunksForInput(String input, String expectedMode) {
        Document doc = new Document("d1", input, Map.of());
        var chunks = chunker.split(doc);
        switch (expectedMode) {
            case "EMPTY" -> assertThat(chunks).isEmpty();
            case "SINGLE" -> {
                assertThat(chunks).hasSize(1);
                assertThat(chunks.get(0).getContent()).isEqualTo("Hello world");
            }
            default -> throw new AssertionError("Unexpected mode: " + expectedMode);
        }
    }

    static Stream<Object[]> splitInputProvider() {
        return Stream.of(
                new Object[]{"Hello world", "SINGLE"},
                new Object[]{" ", "EMPTY"}
        );
    }

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
    void rejectsInvalidMaxSize() {
        assertThrows(
                IllegalArgumentException.class, () -> new RecursiveCharacterChunker(0, 0));
    }

    @ParameterizedTest(name = "preserves all chars from \"{0}\"")
    @MethodSource("oversizedContentProvider")
    void splitsOversizedContentPreservingAllCharacters(String input, char expectedChar, int minCount) {
        Document doc = new Document("d1", input, Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).isNotEmpty();
        long total = chunks.stream()
                .mapToLong(c -> c.getContent().chars().filter(ch -> ch == expectedChar).count())
                .sum();
        assertThat(total).isGreaterThanOrEqualTo(minCount);
    }

    static Stream<Object[]> oversizedContentProvider() {
        return Stream.of(
                new Object[]{"A".repeat(100), 'A', 100},
                new Object[]{"B".repeat(80) + " and some normal words here", 'B', 80},
                new Object[]{"short part " + "L".repeat(80), 'L', 80}
        );
    }

    @Test
    void skipsBlankChunkFromTrailingSeparator() {
        Document doc = new Document("d1", "Sentence one. ", Map.of());
        var chunks = chunker.split(doc);
        assertThat(chunks).noneSatisfy(
                c -> assertThat(c.getContent().trim()).isEmpty());
    }
}