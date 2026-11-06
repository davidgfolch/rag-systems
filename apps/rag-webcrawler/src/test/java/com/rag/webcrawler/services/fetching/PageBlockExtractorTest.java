package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.PageBlockDTO;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageBlockExtractorTest {

    private final PageBlockExtractor sut = new PageBlockExtractor();

    @Test
    void emitsTitleHeadingAndParagraphWithSectionPath() {
        var html = """
                <html><head><title>Docs</title></head><body>
                  <h1>Guide</h1>
                  <p>Intro text.</p>
                  <h2>Setup</h2>
                  <p>Install it.</p>
                </body></html>
                """;
        var blocks = sut.extract(Jsoup.parse(html));
        assertThat(blocks.getFirst().getType()).isEqualTo(PageBlockExtractor.TITLE);
        var install = blocks.stream()
                .filter(block -> block.getText().contains("Install")).findFirst().orElseThrow();
        assertThat(install.getType()).isEqualTo(PageBlockExtractor.PARAGRAPH);
        assertThat(install.getSectionPath()).containsExactly("Guide", "Setup");
        assertThat(install.getDomPath()).isNotBlank();
    }

    @Test
    void emitsListsTablesCodeAndQuotes() {
        var html = """
                <html><body>
                  <table><tr><th>Name</th><th>Age</th></tr><tr><td>Ada</td><td>36</td></tr></table>
                  <ul><li>first</li><li>second</li></ul>
                  <pre>int x = 1;</pre>
                  <blockquote>quoted</blockquote>
                </body></html>
                """;
        var blocks = sut.extract(Jsoup.parse(html));
        assertThat(blocks).extracting(PageBlockDTO::getType)
                .contains(PageBlockExtractor.TABLE, PageBlockExtractor.LIST,
                        PageBlockExtractor.CODE, PageBlockExtractor.QUOTE);
        var table = blocks.stream()
                .filter(block -> PageBlockExtractor.TABLE.equals(block.getType())).findFirst().orElseThrow();
        assertThat(table.getText()).contains("Name | Age").contains("Ada | 36");
    }

    @Test
    void walksContainersAndSkipsNavigationAndScripts() {
        var html = """
                <html><body>
                  <nav>skip me</nav><script>hidden()</script>
                  <div><section><p>keep me</p></section></div>
                </body></html>
                """;
        var blocks = sut.extract(Jsoup.parse(html));
        assertThat(blocks).extracting(PageBlockDTO::getText)
                .containsExactly("keep me");
    }

    @Test
    void omitsTheTitleBlockWhenThePageHasNoTitle() {
        var blocks = sut.extract(Jsoup.parse("<html><body><p>text</p></body></html>"));
        assertThat(blocks).isNotEmpty()
                .extracting(PageBlockDTO::getType)
                .doesNotContain(PageBlockExtractor.TITLE);
    }
}
