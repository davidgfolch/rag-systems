package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;
import static com.rag.common.core.domain.MetadataKeys.TABLE_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

class XhtmlBlockExtractorTest {

    @Test
    void tracksHeadingHierarchyInSectionPaths() {
        var html = """
                <html><body>
                  <h1>Guide</h1>
                  <h2>Setup</h2>
                  <p>Install it.</p>
                </body></html>
                """;
        var blocks = new XhtmlBlockExtractor(false).extract(Jsoup.parse(html));
        var paragraph = blocks.stream()
                .filter(block -> block.content().contains("Install")).findFirst().orElseThrow();
        assertThat(paragraph.type()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(paragraph.sectionPath()).containsExactly("Guide", "Setup");
    }

    @Test
    void incrementsPageForTikaPageMarkers() {
        var html = """
                <html><body>
                  <div class="page"><p>first page</p></div>
                  <div class="page"><p>second page</p></div>
                </body></html>
                """;
        var blocks = new XhtmlBlockExtractor(false).extract(Jsoup.parse(html));
        assertThat(blocks).extracting(block -> block.location().page()).containsExactly(1, 2);
    }

    @Test
    void detectsHeuristicHeadingsWhenEnabled() {
        var html = "<html><body><p>Introduction</p><p>Body text.</p></body></html>";
        var blocks = new XhtmlBlockExtractor(true).extract(Jsoup.parse(html));
        assertThat(blocks.getFirst().type()).isEqualTo(BlockType.HEADING);
        assertThat(blocks.get(1).type()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(blocks.get(1).sectionPath()).containsExactly("Introduction");
    }

    @Test
    void keepsTablesWithHeaderAndListsAndCode() {
        var html = """
                <html><body>
                  <table><tr><th>Name</th><th>Age</th></tr><tr><td>Ada</td><td>36</td></tr></table>
                  <ul><li>first</li><li>second</li></ul>
                  <pre data-language="python">print(1)</pre>
                  <blockquote>quoted</blockquote>
                </body></html>
                """;
        var blocks = new XhtmlBlockExtractor(false).extract(Jsoup.parse(html));
        var table = blocks.stream().filter(block -> block.type() == BlockType.TABLE).findFirst().orElseThrow();
        assertThat(table.content()).contains("Name | Age").contains("Ada | 36");
        assertThat(table.metadata()).containsEntry(TABLE_HEADER, "Name | Age");
        assertThat(blocks).extracting(DocumentBlock::type)
                .contains(BlockType.LIST, BlockType.CODE, BlockType.QUOTE);
        var code = blocks.stream().filter(block -> block.type() == BlockType.CODE).findFirst().orElseThrow();
        assertThat(code.metadata()).containsEntry(CODE_LANGUAGE, "python");
    }

    @Test
    void skipsNavigationAndScripts() {
        var html = """
                <html><body>
                  <nav>skip me</nav><script>hidden()</script>
                  <p>keep me</p>
                </body></html>
                """;
        var blocks = new XhtmlBlockExtractor(false).extract(Jsoup.parse(html));
        assertThat(blocks).extracting(DocumentBlock::content).containsExactly("keep me");
    }

    @Test
    void fallsBackToWholeTextWhenNoBlockIsFound() {
        var blocks = new XhtmlBlockExtractor(false).extract(Jsoup.parse("<html><body>plain words</body></html>"));
        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().content()).isEqualTo("plain words");
        assertThat(blocks.getFirst().type()).isEqualTo(BlockType.PARAGRAPH);
    }
}
