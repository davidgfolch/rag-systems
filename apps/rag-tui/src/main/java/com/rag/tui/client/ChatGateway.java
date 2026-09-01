package com.rag.tui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.contract.ws.ChatEvent;
import com.rag.contract.ws.ChatRequest;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streaming chat client over WebSocket (/ws/chat) for the active module.
 * ask() blocks until done/error, feeding tokens to a sink; cancel() closes the
 * active session so the module disposes the in-flight LLM stream.
 */
public class ChatGateway {

    private final ModuleRegistry registry;
    private final WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<WebSocketSession> active = new AtomicReference<>();

    public ChatGateway(ModuleRegistry registry, WebSocketClient webSocketClient, ObjectMapper objectMapper) {
        this.registry = registry;
        this.webSocketClient = webSocketClient;
        this.objectMapper = objectMapper;
    }

    public String ask(String question, int topK, Consumer<String> onToken) {
        Module module = registry.active();
        String conversationId = "tui-" + System.currentTimeMillis();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>();

        WebSocketSession session = connect(module, done, answer, error, onToken, conversationId);
        sendAsk(session, question, topK, conversationId);
        await(done, error, answer);
        return answer.get();
    }

    public void cancel() {
        WebSocketSession session = active.get();
        if (session != null) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    private WebSocketSession connect(Module module, CountDownLatch done, AtomicReference<String> answer,
                                     AtomicReference<String> error, Consumer<String> onToken,
                                     String conversationId) {
        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                ChatEvent event = objectMapper.readValue(message.getPayload(), ChatEvent.class);
                switch (event.type()) {
                    case "token" -> onToken.accept(event.content());
                    case "done" -> {
                        answer.set(event.content());
                        done.countDown();
                    }
                    case "error" -> {
                        error.set(event.content());
                        done.countDown();
                    }
                    default -> {
                    }
                }
            }
        };
        WebSocketSession session = webSocketClient.execute(handler, module.wsUrl()).join();
        active.set(session);
        return session;
    }

    private void sendAsk(WebSocketSession session, String question, int topK, String conversationId) {
        ChatRequest ask = new ChatRequest("ask", question, topK, conversationId);
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ask)));
        } catch (IOException e) {
            throw new ChatException("Failed to send ask frame", e);
        }
    }

    private void await(CountDownLatch done, AtomicReference<String> error, AtomicReference<String> answer) {
        try {
            if (!done.await(60, TimeUnit.SECONDS)) {
                throw new ChatException("Timed out waiting for chat answer", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatException("Interrupted while waiting for chat answer", e);
        }
        if (error.get() != null) {
            throw new ChatException(error.get(), null);
        }
    }

    public static class ChatException extends RuntimeException {
        public ChatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}