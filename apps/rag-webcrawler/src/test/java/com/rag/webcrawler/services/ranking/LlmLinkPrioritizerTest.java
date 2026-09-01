package com.rag.webcrawler.services.ranking;

import com.rag.common.services.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmLinkPrioritizerTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final LinkPrioritizer fallback = new DeterministicLinkPrioritizer();
    private final LlmLinkPrioritizer sut = new LlmLinkPrioritizer(chatModel, fallback);

    @Test
    void fallsBackWhenNoQuestion() {
        List<String> links = List.of("https://a", "https://b");

        List<String> result = sut.prioritize(links, "  ");

        assertThat(result).containsExactly("https://a", "https://b");
    }

    @Test
    void usesModelRankedUrls() {
        List<String> links = List.of("https://ex.com/a", "https://ex.com/b");
        when(chatModel.complete(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://ex.com/b\nhttps://ex.com/a\n");

        List<String> result = sut.prioritize(links, "hello world");

        assertThat(result).containsExactly("https://ex.com/b", "https://ex.com/a");
    }

    @Test
    void fallsBackWhenModelReturnsNothingUsable() {
        List<String> links = List.of("https://ex.com/a", "https://ex.com/b");
        when(chatModel.complete(org.mockito.ArgumentMatchers.anyString())).thenReturn("I don't know.");

        List<String> result = sut.prioritize(links, "hello world");

        assertThat(result.get(0)).isEqualTo("https://ex.com/a");
    }
}