package com.rag.webcrawler.services.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicLinkPrioritizerTest {

    private final DeterministicLinkPrioritizer sut = new DeterministicLinkPrioritizer();

    @Test
    void keepsLinksInOrderWithoutQuestion() {
        List<String> links = List.of("https://a", "https://b");

        List<String> result = sut.prioritize(links, null);

        assertThat(result).containsExactly("https://a", "https://b");
    }

    @Test
    void boostsLinksMatchingQuestionWords() {
        List<String> links = List.of("https://ex.com/java", "https://ex.com/python", "https://ex.com/news");
        List<String> result = sut.prioritize(links, "java");

        assertThat(result.get(0)).isEqualTo("https://ex.com/java");
    }

    @Test
    void prefersRelativeLinksWhenNoQuestion() {
        List<String> result = sut.prioritize(List.of("https://ex.com/x.html", "/docs/guide.html"), "");

        assertThat(result.get(0)).isEqualTo("/docs/guide.html");
    }
}