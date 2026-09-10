package com.rag.provider.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ModelRouter router;
    @Mock private ChatModel chatModel;

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void shouldCompleteWithActiveModel() {
        when(router.chatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("Hello!"));
        var service = new ChatService(router);
        assertThat(service.complete("hi")).isEqualTo("Hello!");
    }

    @Test
    void shouldStreamTokensFromActiveModel() {
        when(router.chatModel()).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(textResponse("Hel"), textResponse("lo")));
        var service = new ChatService(router);
        assertThat(service.stream("hi").collectList().block()).containsExactly("Hel", "lo");
    }
}