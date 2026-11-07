package com.rag.webcrawler.services.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UrlFamilySelectorTest {

    private final UrlFamilySelector sut = new UrlFamilySelector();

    @Test
    void collapsesVersionedCopiesOfTheSamePage() {
        var selected = sut.select(List.of(
                "https://example.com/docs/3.0/web/a",
                "https://example.com/docs/3.1/web/a",
                "https://example.com/docs/latest/web/a",
                "https://example.com/docs/3.0/web/b"), "https://example.com/docs/");

        assertThat(selected).containsExactlyInAnyOrder(
                "https://example.com/docs/3.0/web/a",
                "https://example.com/docs/3.0/web/b");
    }

    @Test
    void prefersTheStartUrlInsideItsFamily() {
        var selected = sut.select(List.of(
                "https://example.com/docs/3.0/web/a",
                "https://example.com/docs/latest/web/a"), "https://example.com/docs/latest/web/a");

        assertThat(selected).containsExactly("https://example.com/docs/latest/web/a");
    }

    @Test
    void keepsDistinctDocumentsApart() {
        var selected = sut.select(List.of(
                "https://example.com/docs/a",
                "https://example.com/docs/b"), "https://example.com/docs/");

        assertThat(selected).containsExactlyInAnyOrder(
                "https://example.com/docs/a",
                "https://example.com/docs/b");
    }

    @Test
    void returnsEmptyForNoInput() {
        assertThat(sut.select(null, "https://example.com")).isEmpty();
        assertThat(sut.select(List.of(), "https://example.com")).isEmpty();
    }

    @Test
    void ignoresUnparseableUrls() {
        assertThat(sut.select(List.of("not a url"), "https://example.com")).isEmpty();
    }
}
