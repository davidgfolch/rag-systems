package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTypeTest {

    @Test
    void exposesEverySupportedFormatWithARoundTrippingName() {
        assertThat(SourceType.values()).containsExactly(
                SourceType.PDF, SourceType.DOCX, SourceType.HTML, SourceType.MARKDOWN,
                SourceType.JSON, SourceType.TEXT, SourceType.UNKNOWN);
        for (var type : SourceType.values()) {
            assertThat(SourceType.valueOf(type.name())).isEqualTo(type);
        }
    }

    @Test
    void exposesEveryBlockTypeWithARoundTrippingName() {
        assertThat(BlockType.values()).containsExactly(
                BlockType.HEADING, BlockType.PARAGRAPH, BlockType.CODE, BlockType.TABLE,
                BlockType.LIST, BlockType.QUOTE, BlockType.JSON_OBJECT, BlockType.JSON_ARRAY,
                BlockType.TITLE);
        for (var type : BlockType.values()) {
            assertThat(BlockType.valueOf(type.name())).isEqualTo(type);
        }
    }
}
