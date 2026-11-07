package com.rag.webcrawler.services.fetching;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NavLinkExtractorTest {

    private final NavLinkExtractor sut = new NavLinkExtractor();

    @Test
    void collectsLinksFromNavigationContainersOnly() {
        var html = """
                <html><body>
                  <nav><a href="/guide">Guide</a><a href="/faq">FAQ</a></nav>
                  <aside><a href="/aside">Aside</a></aside>
                  <div role="navigation"><a href="/role">Role</a></div>
                  <div class="sidebar"><a href="/side">Side</a></div>
                  <main><a href="/content">Content</a></main>
                </body></html>
                """;
        var links = sut.extract(Jsoup.parse(html, "https://example.com/docs/"));

        assertThat(links).containsExactly(
                "https://example.com/guide",
                "https://example.com/faq",
                "https://example.com/aside",
                "https://example.com/role",
                "https://example.com/side")
                .doesNotContain("https://example.com/content");
    }

    @Test
    void ignoresBlankHrefsAndDuplicates() {
        var html = """
                <html><body>
                  <nav><a href="">blank</a><a href="/a">A</a><a href="/a">A again</a></nav>
                </body></html>
                """;

        assertThat(sut.extract(Jsoup.parse(html, "https://example.com/")))
                .containsExactly("https://example.com/a");
    }

    @Test
    void returnsEmptyWithoutNavigation() {
        assertThat(sut.extract(Jsoup.parse("<html><body><p>text</p></body></html>"))).isEmpty();
    }
}
