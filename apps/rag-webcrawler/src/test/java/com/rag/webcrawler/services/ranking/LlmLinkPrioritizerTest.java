package com.rag.webcrawler.services.ranking;

import com.rag.common.services.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmLinkPrioritizerTest {

    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private final LinkPrioritizer fallback = new DeterministicLinkPrioritizer();
    private final LlmLinkPrioritizer sut = new LlmLinkPrioritizer(chatModel, fallback);

    @Test
    void fallsBackWhenNoQuestion() {
        var links = List.of("https://a", "https://b");
        var result = sut.prioritize(links, "  ");
        assertThat(result).containsExactly("https://a", "https://b");
    }

    @Test
    void usesModelRankedUrls() {
        var links = List.of("https://ex.com/a", "https://ex.com/b");
        when(chatModel.complete(anyString()))
                .thenReturn("https://ex.com/b\nhttps://ex.com/a\n");
        var result = sut.prioritize(links, "hello world");
        assertThat(result).containsExactly("https://ex.com/b", "https://ex.com/a");
    }

    @Test
    void fallsBackWhenModelReturnsNothingUsable() {
        var links = List.of("https://ex.com/a", "https://ex.com/b");
        when(chatModel.complete(anyString())).thenReturn("I don't know.");
        var result = sut.prioritize(links, "hello world");
        assertThat(result.get(0)).isEqualTo("https://ex.com/a");
    }
}