package com.rag.common.services.chunking;

import com.rag.common.domain.Document;
import com.rag.common.domain.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker(20, 4);

    @ParameterizedTest(name = "input=\"{0}\" → {1}")
    @MethodSource("splitInputProvider")
    void splitReturnsExpectedChunksForInput(String input, String expectedMode) {
        Document doc = new Document("d1", input, Map.of());
        var chunks = chunker.split(doc);

        switch (expectedMode) {
            case "MULTI" -> {
                assertThat(chunks).hasSizeGreaterThan(1);
                assertThat(chunks.get(0).getDocumentId()).isEqualTo("d1");
                assertThat(chunks.get(0).getMetadata()).containsEntry(MetadataKeys.STRATEGY, "fixed");
            }
            case "EMPTY" -> assertThat(chunks).isEmpty();
            case "SINGLE" -> assertThat(chunks).hasSize(1);
        }
    }

    static Stream<Object[]> splitInputProvider() {
        return Stream.of(
                new Object[]{"This is a fairly long piece of content that needs splitting.", "MULTI"},
                new Object[]{"   ", "EMPTY"},
                new Object[]{"Short text", "SINGLE"}
        );
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new FixedSizeChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunker(10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
