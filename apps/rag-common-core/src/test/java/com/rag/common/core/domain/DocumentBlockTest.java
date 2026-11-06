package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentBlockTest {

    @Test
    void exposesTheImmediateHeading() {
        var block = new DocumentBlock(BlockType.PARAGRAPH, "text", List.of("Guide", "Setup"),
                SourceLocation.NONE, Map.of());
        assertThat(block.heading()).isEqualTo("Setup");
        assertThat(new DocumentBlock(BlockType.PARAGRAPH, "text").heading()).isNull();
    }

    @Test
    void detectsBlankContent() {
        assertThat(new DocumentBlock(BlockType.PARAGRAPH, "text").isBlank()).isFalse();
        assertThat(new DocumentBlock(BlockType.PARAGRAPH, " ").isBlank()).isTrue();
        assertThat(new DocumentBlock(BlockType.PARAGRAPH, null).isBlank()).isTrue();
    }

    @Test
    void appliesSafeDefaultsForNullComponents() {
        var block = new DocumentBlock(null, null, null, null, null);
        assertThat(block.type()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(block.content()).isEmpty();
        assertThat(block.sectionPath()).isEmpty();
        assertThat(block.location()).isEqualTo(SourceLocation.NONE);
        assertThat(block.metadata()).isEmpty();
    }

    @Test
    void comparesByValue() {
        var block = new DocumentBlock(BlockType.CODE, "code", List.of("Guide"),
                new SourceLocation(1, "pre", null, null, null), Map.of("k", "v"));
        var same = new DocumentBlock(BlockType.CODE, "code", List.of("Guide"),
                new SourceLocation(1, "pre", null, null, null), Map.of("k", "v"));
        assertThat(block).isEqualTo(same).hasSameHashCodeAs(same)
                .isNotEqualTo(new DocumentBlock(BlockType.CODE, "other"));
        assertThat(block.toString()).contains("code");
    }
}
