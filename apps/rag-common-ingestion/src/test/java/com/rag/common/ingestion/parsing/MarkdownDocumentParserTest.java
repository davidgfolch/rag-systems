package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser sut = new MarkdownDocumentParser();

    @Test
    void keepsHeadingHierarchyInSectionPath() {
        var document = new Document("d1", """
                # Guide
                Intro text.

                ## Setup
                Install it.
                """, Map.of());
        var parsed = sut.parse(document);
        assertThat(parsed.sourceType()).isEqualTo(SourceType.MARKDOWN);
        assertThat(parsed.metadata()).containsEntry(FORMAT, "MARKDOWN");
        assertThat(parsed.blocks()).extracting(DocumentBlock::type)
                .contains(BlockType.HEADING, BlockType.PARAGRAPH);
        var install = parsed.blocks().stream()
                .filter(block -> block.content().contains("Install")).findFirst().orElseThrow();
        assertThat(install.sectionPath()).containsExactly("Guide", "Setup");
    }

    @Test
    void emitsFencedCodeWithLanguageMetadata() {
        var document = new Document("d1", """
                ```java
                int x = 1;
                ```
                """, Map.of());
        var code = sut.parse(document).blocks().getFirst();
        assertThat(code.type()).isEqualTo(BlockType.CODE);
        assertThat(code.content()).isEqualTo("int x = 1;");
        assertThat(code.metadata()).containsEntry(CODE_LANGUAGE, "java");
    }

    @Test
    void emitsGfmTablesAndLists() {
        var document = new Document("d1", """
                | Name | Age |
                | ---- | --- |
                | Ada  | 36  |

                - first
                - second
                """, Map.of());
        var blocks = sut.parse(document).blocks();
        var table = blocks.stream().filter(block -> block.type() == BlockType.TABLE).findFirst().orElseThrow();
        assertThat(table.content()).contains("Name | Age").contains("Ada | 36");
        var list = blocks.stream().filter(block -> block.type() == BlockType.LIST).findFirst().orElseThrow();
        assertThat(list.content()).contains("first").contains("second");
    }

    @Test
    void emitsBlockQuotes() {
        var document = new Document("d1", "> quoted text", Map.of());
        var quote = sut.parse(document).blocks().getFirst();
        assertThat(quote.type()).isEqualTo(BlockType.QUOTE);
        assertThat(quote.content()).contains("quoted text");
    }
}
