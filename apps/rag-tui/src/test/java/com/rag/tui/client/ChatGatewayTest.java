package com.rag.tui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatGatewayTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(new Module("rag-basic", "http://localhost:8081")), "rag-basic");
    private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final ChatGateway sut = new ChatGateway(registry, webSocketClient, new ObjectMapper());
    private final List<String> tokens = new ArrayList<>();
    private TextWebSocketHandler handler;
    private Thread runner;

    private void startAsk() throws Exception {
        when(webSocketClient.execute(any(), anyString())).thenReturn(
                CompletableFuture.completedFuture(session));
        doNothing().when(session).sendMessage(any(TextMessage.class));
        runner = new Thread(() -> sut.ask("hello", 4, tokens::add));
        runner.start();
        org.mockito.ArgumentCaptor<WebSocketHandler> captor =
                org.mockito.ArgumentCaptor.forClass(WebSocketHandler.class);
        verify(webSocketClient, timeout(2000)).execute(captor.capture(), anyString());
        handler = (TextWebSocketHandler) captor.getValue();
    }

    private void feedEvent(WebSocketSession session, String json) throws Exception {
        handler.handleMessage(session, new TextMessage(json));
    }

    @Test
    void streamsTokensUntilDone() throws Exception {
        startAsk();
        feedEvent(session, "{\"type\":\"token\",\"content\":\"Hel\",\"conversationId\":\"x\"}");
        feedEvent(session, "{\"type\":\"token\",\"content\":\"lo\",\"conversationId\":\"x\"}");
        feedEvent(session, "{\"type\":\"done\",\"content\":\"Hello\",\"conversationId\":\"x\"}");
        assertThat(tokens).containsExactly("Hel", "lo");
    }

    @Test
    void cancelsActiveSession() throws Exception {
        startAsk();
        sut.cancel();
        verify(session).close();
    }

    @Test
    void throwsWhenModuleReportsError() throws Exception {
        startAsk();
        feedEvent(session, "{\"type\":\"error\",\"content\":\"boom\",\"conversationId\":\"x\"}");
        runner.join(2000);
        assertThat(runner.isAlive()).isFalse();
    }

    @Test
    void cancelWithoutActiveSessionDoesNothing() throws Exception {
        sut.cancel();
        verify(session, org.mockito.Mockito.never()).close();
    }
}