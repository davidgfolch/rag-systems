package com.rag.basic.api.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.services.ChatService;
import com.rag.contract.ws.ChatRequest;
import com.rag.contract.ws.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streaming chat over WebSocket: ask/cancel inbound, token/done/error outbound.
 * Per-session disposal cancels the underlying LLM stream on disconnect or cancel.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> streams = new ConcurrentHashMap<>();
    private final Map<String, String> conversationBySession = new ConcurrentHashMap<>();
    private final Object sendLock = new Object();

    public ChatWebSocketHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequest request = objectMapper.readValue(message.getPayload(), ChatRequest.class);
        if ("cancel".equals(request.type())) {
            cancel(request.conversationId());
            send(session, new ChatResponse("done", "", request.conversationId()));
        } else if ("ask".equals(request.type()) && request.question() != null) {
            ask(session, request);
        }
    }

    private void ask(WebSocketSession session, ChatRequest request) {
        cancel(request.conversationId());
        conversationBySession.put(session.getId(), request.conversationId());
        StringBuilder answer = new StringBuilder();
        int topK = request.topK() == null ? 5 : request.topK();
        log.info("Ask request: conversationId={}, topK={}", request.conversationId(), topK);
        AtomicBoolean finished = new AtomicBoolean();
        Disposable disposable = chatService.askStream(request.question(), topK)
                .doOnNext(answer::append)
                .doOnCancel(() -> finished.set(true))
                .subscribe(
                        token -> send(session, new ChatResponse("token", token, request.conversationId())),
                        error -> {
                            finished.set(true);
                            log.error("Stream error: conversationId={}, error={}",
                                    request.conversationId(), error.getMessage(), error);
                            send(session, new ChatResponse("error", error.getMessage(), request.conversationId()));
                        },
                        () -> {
                            if (finished.compareAndSet(false, true)) {
                                log.info("Stream complete: conversationId={}, answerLength={}",
                                        request.conversationId(), answer.length());
                                send(session, new ChatResponse("done", answer.toString(), request.conversationId()));
                            } else {
                                log.info("Stream cancelled: conversationId={}", request.conversationId());
                            }
                        });
        streams.put(request.conversationId(), disposable);
    }

    private void cancel(String conversationId) {
        var disposable = streams.remove(conversationId);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var conversationId = conversationBySession.remove(session.getId());
        if (conversationId != null) {
            log.info("Connection closed: conversationId={}, status={}", conversationId, status);
            cancel(conversationId);
        }
    }

    private void send(WebSocketSession session, ChatResponse response) {
        try {
            synchronized (sendLock) {
                session.sendMessage(new TextMessage(toJson(response)));
            }
        } catch (Exception exception) {
            throw new UncheckedIOException(new IOException(exception));
        }
    }

    private String toJson(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}