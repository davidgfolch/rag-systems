package com.rag.webcrawler.services.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralOrderTest {

    @Test
    void interleavesDirectoriesSoASmallBudgetSamplesTheTree() {
        var ordered = StructuralOrder.order(List.of(
                "https://example.com/a/2",
                "https://example.com/a/1",
                "https://example.com/b/2",
                "https://example.com/b/1"));

        assertThat(ordered).containsExactly(
                "https://example.com/a/1",
                "https://example.com/b/1",
                "https://example.com/a/2",
                "https://example.com/b/2");
    }

    @Test
    void keepsAllUrlsAndHandlesEmptyInput() {
        var urls = List.of("https://example.com/a/one", "https://example.com/a/two");
        assertThat(StructuralOrder.order(urls)).containsExactlyElementsOf(urls);
        assertThat(StructuralOrder.order(List.of())).isEmpty();
    }

    @Test
    void toleratesMalformedUrls() {
        assertThat(StructuralOrder.order(List.of("not a url"))).containsExactly("not a url");
    }
}
