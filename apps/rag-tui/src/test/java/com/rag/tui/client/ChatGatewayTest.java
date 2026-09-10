package com.rag.tui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.testfixture.TestModules;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;

class ChatGatewayTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(TestModules.basic()), TestModules.BASIC);
    private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final ChatGateway sut = new ChatGateway(registry, webSocketClient, new ObjectMapper(), 60);
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
        feedEvent(session, "{\"type\":\"" + TOKEN + "\",\"content\":\"Hel\",\"conversationId\":\"x\"}");
        feedEvent(session, "{\"type\":\"" + TOKEN + "\",\"content\":\"lo\",\"conversationId\":\"x\"}");
        feedEvent(session, "{\"type\":\"" + DONE + "\",\"content\":\"Hello\",\"conversationId\":\"x\"}");
        runner.join(2000);
        assertThat(runner.isAlive()).isFalse();
        assertThat(tokens).containsExactly("Hel", "lo");
    }

    @Test
    void cancelsActiveSession() throws Exception {
        startAsk();
        sut.cancel();
        verify(session).close();
        feedEvent(session, "{\"type\":\"" + DONE + "\",\"content\":\"\",\"conversationId\":\"x\"}");
        runner.join(2000);
        assertThat(runner.isAlive()).isFalse();
    }

    @Test
    void throwsWhenModuleReportsError() throws Exception {
        startAsk();
        feedEvent(session, "{\"type\":\"" + ERROR + "\",\"content\":\"boom\",\"conversationId\":\"x\"}");
        runner.join(2000);
        assertThat(runner.isAlive()).isFalse();
    }

    @Test
    void cancelWithoutActiveSessionDoesNothing() throws Exception {
        sut.cancel();
        verify(session, never()).close();
    }
}