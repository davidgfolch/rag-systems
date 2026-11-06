package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedDocumentTest {

    private static final DocumentBlock FIRST = new DocumentBlock(BlockType.PARAGRAPH, "first");
    private static final DocumentBlock SECOND = new DocumentBlock(BlockType.PARAGRAPH, "second");

    @Test
    void joinsNonBlankBlocksIntoPlainText() {
        var document = new ParsedDocument("d1", SourceType.TEXT, Map.of(),
                List.of(FIRST, new DocumentBlock(BlockType.PARAGRAPH, " "), SECOND));
        assertThat(document.text()).isEqualTo("first\n\nsecond");
    }

    @Test
    void isBlankWhenEveryBlockIsBlank() {
        var blank = new ParsedDocument("d1", SourceType.TEXT, Map.of(),
                List.of(new DocumentBlock(BlockType.PARAGRAPH, " ")));
        assertThat(blank.isBlank()).isTrue();
        assertThat(new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of()).isBlank()).isTrue();
        assertThat(new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of(FIRST)).isBlank()).isFalse();
    }

    @Test
    void appliesSafeDefaultsForNullComponents() {
        var document = new ParsedDocument("d1", null, null, null);
        assertThat(document.sourceType()).isEqualTo(SourceType.UNKNOWN);
        assertThat(document.metadata()).isEmpty();
        assertThat(document.blocks()).isEmpty();
    }

    @Test
    void rejectsANullDocumentId() {
        assertThatThrownBy(() -> new ParsedDocument(null, SourceType.TEXT, Map.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void comparesByValue() {
        var one = new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of(FIRST));
        var same = new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of(FIRST));
        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same)
                .isNotEqualTo(new ParsedDocument("d2", SourceType.TEXT, Map.of(), List.of(FIRST)));
        assertThat(one.toString()).contains("d1");
    }
}
