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

class TokenChunkerTest {

    private final TokenChunker chunker = new TokenChunker(5, 1);

    @ParameterizedTest(name = "input=\"{0}\" → {1}")
    @MethodSource("splitInputProvider")
    void splitReturnsExpectedChunksForInput(String input, String expectedMode) {
        Document doc = new Document("d1", input, Map.of());
        var chunks = chunker.split(doc);
        switch (expectedMode) {
            case "MULTI" -> {
                assertThat(chunks).hasSizeGreaterThan(1);
                assertThat(chunks.get(0).getMetadata()).containsEntry(MetadataKeys.STRATEGY, "token");
            }
            case "EMPTY" -> assertThat(chunks).isEmpty();
            case "SINGLE" -> assertThat(chunks).hasSize(1);
            default -> throw new AssertionError("Unexpected mode: " + expectedMode);
        }
    }

    static Stream<Object[]> splitInputProvider() {
        return Stream.of(
                new Object[]{"one two three four five six seven eight nine ten eleven", "MULTI"},
                new Object[]{"", "EMPTY"},
                new Object[]{"one two three", "SINGLE"}
        );
    }

    @Test
    void rejectsInvalidMaxTokens() {
        assertThrows(
                IllegalArgumentException.class, () -> new TokenChunker(0, 0));
    }
}