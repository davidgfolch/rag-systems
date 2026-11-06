package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.TABLE_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

class HtmlDocumentParserTest {

    private final HtmlDocumentParser sut = new HtmlDocumentParser();

    @Test
    void extractsTitleHeadingsAndParagraphsWithSectionPaths() {
        var html = """
                <html><head><title>Docs</title></head><body>
                  <h1>Guide</h1>
                  <p>Intro.</p>
                  <h2>Setup</h2>
                  <p>Install it.</p>
                </body></html>
                """;
        var parsed = sut.parse(new Document("d1", html, Map.of()));
        assertThat(parsed.sourceType()).isEqualTo(SourceType.HTML);
        assertThat(parsed.metadata()).containsEntry(FORMAT, "HTML");
        assertThat(parsed.blocks().getFirst().type()).isEqualTo(BlockType.TITLE);
        var install = parsed.blocks().stream()
                .filter(block -> block.content().contains("Install")).findFirst().orElseThrow();
        assertThat(install.sectionPath()).containsExactly("Guide", "Setup");
    }

    @Test
    void keepsTablesListsAndCodeBlocks() {
        var html = """
                <html><body>
                  <table><tr><th>Name</th><th>Age</th></tr><tr><td>Ada</td><td>36</td></tr></table>
                  <ul><li>first</li><li>second</li></ul>
                  <pre class="language-java">int x = 1;</pre>
                </body></html>
                """;
        var blocks = sut.parse(new Document("d1", html, Map.of())).blocks();
        var table = blocks.stream().filter(block -> block.type() == BlockType.TABLE).findFirst().orElseThrow();
        assertThat(table.content()).contains("Name | Age");
        assertThat(table.metadata()).containsEntry(TABLE_HEADER, "Name | Age");
        assertThat(blocks).extracting(DocumentBlock::type).contains(BlockType.LIST, BlockType.CODE);
        var code = blocks.stream().filter(block -> block.type() == BlockType.CODE).findFirst().orElseThrow();
        assertThat(code.metadata()).containsEntry(CODE_LANGUAGE, "java");
    }
}
