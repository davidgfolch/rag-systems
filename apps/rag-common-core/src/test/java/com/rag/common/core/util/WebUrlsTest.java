package com.rag.common.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class WebUrlsTest {

    @Test
    void parsesHttpAndHttpsUrls() {
        assertThat(WebUrls.parse("https://example.com/page")).contains(URI.create("https://example.com/page"));
        assertThat(WebUrls.parse("http://example.com")).contains(URI.create("http://example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/page", "mailto:a@example.com", "not a url", ""})
    void rejectsNonWebUrls(String raw) {
        assertThat(WebUrls.parse(raw)).isEmpty();
        assertThat(WebUrls.key(raw)).isNull();
    }

    @Test
    void rejectsNullUrl() {
        assertThat(WebUrls.parse(null)).isEmpty();
        assertThat(WebUrls.key(null)).isNull();
    }

    @Test
    void normalizesSchemeQueryFragmentTrailingSlashAndHostCase() {
        assertThat(WebUrls.key("HTTPS://Example.COM/Guide/?q=1#intro"))
                .isEqualTo("example.com/Guide");
        assertThat(WebUrls.key("http://example.com/Guide"))
                .isEqualTo("example.com/Guide");
        assertThat(WebUrls.key("https://example.com/"))
                .isEqualTo("example.com/");
        assertThat(WebUrls.key("https://example.com"))
                .isEqualTo("example.com/");
    }

    @Test
    void keyOfUsesParsedUri() {
        assertThat(WebUrls.keyOf(URI.create("https://Example.com/a/"))).isEqualTo("example.com/a");
    }

    @Test
    void hostIsLowerCasedAndEmptyForInvalidInput() {
        assertThat(WebUrls.host("https://Example.COM/a")).isEqualTo("example.com");
        assertThat(WebUrls.host("nonsense")).isEmpty();
    }

    @Test
    void pathIsRootWhenMissingOrSingleSlash() {
        assertThat(WebUrls.pathOf(URI.create("https://example.com"))).isEqualTo("/");
        assertThat(WebUrls.pathOf(URI.create("https://example.com/"))).isEqualTo("/");
        assertThat(WebUrls.pathOf(URI.create("https://example.com/a/"))).isEqualTo("/a");
    }
}
