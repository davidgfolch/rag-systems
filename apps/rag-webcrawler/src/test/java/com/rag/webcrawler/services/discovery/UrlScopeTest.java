package com.rag.webcrawler.services.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlScopeTest {

    private static final String START = "https://docs.example.com/guide/";

    private final UrlScope sut = UrlScope.of(START);

    @Test
    void acceptsPagesUnderTheStartDirectory() {
        assertThat(sut.accepts(START)).isTrue();
        assertThat(sut.accepts(START + "intro")).isTrue();
        assertThat(sut.accepts(START + "deep/page.html")).isTrue();
    }

    @Test
    void rejectsOtherOriginsAndSiblingDirectories() {
        assertThat(sut.accepts("https://other.example.com/guide/intro")).isFalse();
        assertThat(sut.accepts("https://docs.example.com/other/intro")).isFalse();
    }

    @Test
    void rejectsNonHtmlAssets() {
        assertThat(sut.accepts(START + "logo.png")).isFalse();
        assertThat(sut.accepts(START + "manual.pdf")).isFalse();
        assertThat(sut.accepts(START + "index.html")).isTrue();
    }

    @Test
    void rejectsInteractiveAndPrintUrls() {
        assertThat(sut.accepts(START + "search?q=x")).isFalse();
        assertThat(sut.accepts(START + "login")).isFalse();
        assertThat(sut.accepts(START + "intro?print=true")).isFalse();
    }

    @Test
    void canonicalNormalisesTrailingSlashIndexAndTracking() {
        assertThat(sut.canonical(START + "intro/")).isEqualTo("https://docs.example.com/guide/intro");
        assertThat(sut.canonical(START + "index.html")).isEqualTo("https://docs.example.com/guide/");
        assertThat(sut.canonical(START + "intro?utm_source=x")).isEqualTo("https://docs.example.com/guide/intro");
    }

    @Test
    void canonicalKeepsAndSortsMeaningfulQuery() {
        assertThat(sut.canonical(START + "intro?b=2&a=1&ref=x"))
                .isEqualTo("https://docs.example.com/guide/intro?a=1&b=2");
    }

    @Test
    void usesTheDirectoryOfAFileUrlAsBase() {
        var fromFile = UrlScope.of("https://docs.example.com/guide/index.html");
        assertThat(fromFile.baseDir()).isEqualTo("/guide/");
        assertThat(fromFile.accepts("https://docs.example.com/guide/intro")).isTrue();
    }

    @Test
    void exposesOrigin() {
        assertThat(sut.origin()).isEqualTo("https://docs.example.com");
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThatThrownBy(() -> UrlScope.of("ftp://docs.example.com/guide"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalReturnsNullForAnInvalidUrl() {
        assertThat(sut.canonical("not a url")).isNull();
    }
}
