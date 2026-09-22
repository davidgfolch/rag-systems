package com.rag.advanced.api.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.generation.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatWebSocketHandler handler = new ChatWebSocketHandler(chatService, new ObjectMapper());
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);

    @BeforeEach
    void setUp() {
        reset(session);
        when(session.getId()).thenReturn("s1");
    }

    private void send(String json) throws Exception {
        handler.handleTextMessage(session, new TextMessage(json));
    }

    private List<String> sentPayloads(int expectedCount) throws Exception {
        verify(session, times(expectedCount)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(TextMessage::getPayload).toList();
    }

    @Test
    void streamsTokensThenDone() throws Exception {
        when(chatService.askStream("hello", 5))
                .thenReturn(Flux.just("Hel", "lo!"));
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        List<String> payloads = sentPayloads(3);
        assertThat(payloads).hasSize(3);
        assertThat(payloads.get(0)).contains("\"type\":\"token\"", "\"content\":\"Hel\"", "\"conversationId\":\"c1\"");
        assertThat(payloads.get(1)).contains("\"type\":\"token\"", "\"content\":\"lo!\"");
        assertThat(payloads.get(2)).contains("\"type\":\"done\"", "\"content\":\"Hello!\"", "\"conversationId\":\"c1\"");
        verify(chatService).askStream("hello", 5);
    }

    @Test
    void cancelDisposesStream() throws Exception {
        when(chatService.askStream("hello", 5)).thenReturn(Flux.never());
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\",\"conversationId\":\"c1\"}"));
        List<String> payloads = sentPayloads(1);
        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0)).contains("\"type\":\"done\"", "\"content\":\"\"", "\"conversationId\":\"c1\"");
    }

    @Test
    void ignoresEmptyQuestion() throws Exception {
        send("{\"type\":\"ask\",\"question\":null,\"conversationId\":\"c1\"}");
        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(chatService, never()).askStream(anyString(), anyInt());
    }

    @Test
    void streamsErrorEvent() throws Exception {
        when(chatService.askStream("hello", 5))
                .thenReturn(Flux.error(new IllegalStateException("boom")));
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        List<String> payloads = sentPayloads(1);
        assertThat(payloads.get(0)).contains("\"type\":\"error\"", "\"boom\"");
    }

    @Test
    void usesDefaultTopKWhenNull() throws Exception {
        when(chatService.askStream("hello", 5)).thenReturn(Flux.empty());
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        sentPayloads(1);
        verify(chatService).askStream("hello", 5);
    }

    @Test
    void usesExplicitTopKWhenProvided() throws Exception {
        when(chatService.askStream("hello", 7)).thenReturn(Flux.empty());
        send("{\"type\":\"ask\",\"question\":\"hello\",\"topK\":7,\"conversationId\":\"c1\"}");
        sentPayloads(1);
        verify(chatService).askStream("hello", 7);
    }

    @Test
    void runsWithoutTracerSpanInSessionAttributes() throws Exception {
        when(chatService.askStream("hello", 5)).thenReturn(Flux.just("tok"));
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        List<String> payloads = sentPayloads(2);
        assertThat(payloads.get(0)).contains("\"type\":\"token\"");
        assertThat(payloads.get(1)).contains("\"type\":\"done\"");
    }

    @Test
    void sendsSingleDoneWhenStreamCancelledBeforeCompletion() throws Exception {
        when(chatService.askStream("hello", 5)).thenReturn(Flux.never());
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\",\"conversationId\":\"c1\"}"));
        verify(chatService, atLeastOnce()).askStream(anyString(), anyInt());
    }

    @Test
    void cleansUpOnDisconnect() throws Exception {
        when(chatService.askStream("hello", 5)).thenReturn(Flux.never());
        send("{\"type\":\"ask\",\"question\":\"hello\",\"conversationId\":\"c1\"}");
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\",\"conversationId\":\"c1\"}"));
        verify(chatService).askStream("hello", 5);
    }
}