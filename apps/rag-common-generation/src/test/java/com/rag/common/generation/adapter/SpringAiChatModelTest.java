package com.rag.common.generation.adapter;

import com.rag.common.core.services.ChatModelPort;
import com.rag.common.generation.StreamingChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatModelTest {

    @Test
    void completesPromptViaChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("prompt text")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("the answer");
        ChatModelPort sut = new SpringAiChatModel(chatClient);
        assertThat(sut.complete("prompt text")).isEqualTo("the answer");
        verify(chatClient).prompt();
    }

    @Test
    void returnsNullWhenContentIsNull() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("prompt text")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(null);
        ChatModelPort sut = new SpringAiChatModel(chatClient);
        assertThat(sut.complete("prompt text")).isNull();
    }

    @Test
    void streamsContentViaChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("prompt text")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("He", "llo"));
        StreamingChatModelPort sut = new SpringAiChatModel(chatClient);
        StepVerifier.create(sut.completeStream("prompt text"))
                .expectNext("He", "llo")
                .verifyComplete();
        verify(streamSpec).content();
    }
}