package com.rag.common.generation.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.tracing.TracePropagation;
import com.rag.contract.ws.ChatRequest;
import com.rag.contract.ws.ChatResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.contract.constants.FrameTypes.ASK;
import static com.rag.contract.constants.FrameTypes.CANCEL;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;

/**
 * Streaming chat over WebSocket: ask/cancel inbound, token/done/error outbound.
 * The streaming seam is injected per module ({@link AnswerStreamer}), so this
 * one handler serves rag-basic, rag-advanced and rag-agentic. Per-session
 * disposal cancels the underlying LLM stream on disconnect or cancel.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    public static final String SPAN_ATTRIBUTE = "rag.tracing.span";

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_BYTES = 512 * 1024;

    private final AnswerStreamer answerStreamer;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Map<String, Disposable> streams = new ConcurrentHashMap<>();
    private final Map<String, String> conversationBySession = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AnswerStreamer answerStreamer, ObjectMapper om) {
        this(answerStreamer, om, null);
    }

    public ChatWebSocketHandler(AnswerStreamer answerStreamer, ObjectMapper om, Tracer tracer) {
        this.answerStreamer = answerStreamer;
        this.objectMapper = om;
        this.tracer = tracer;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequest request = objectMapper.readValue(message.getPayload(), ChatRequest.class);
        TracePropagation.runWithSpan(tracer, handshakeSpan(session), () -> handleRequest(session, request));
    }

    private void handleRequest(WebSocketSession session, ChatRequest req) {
        if (CANCEL.equals(req.type())) {
            cancel(req.conversationId());
            send(session, new ChatResponse(DONE, "", req.conversationId()));
        } else if (ASK.equals(req.type()) && req.question() != null) {
            ask(session, req);
        }
    }

    private Span handshakeSpan(WebSocketSession session) {
        return session.getAttributes().get(SPAN_ATTRIBUTE) instanceof Span span ? span : null;
    }

    private void ask(WebSocketSession session, ChatRequest req) {
        String conversationId = req.conversationId();
        cancel(conversationId);
        conversationBySession.put(session.getId(), conversationId);
        StringBuilder answer = new StringBuilder();
        int topK = req.topK() == null ? 5 : req.topK();
        log.info("Ask request: conversationId={}, topK={}", conversationId, topK);
        AtomicBoolean finished = new AtomicBoolean();
        AtomicReference<Disposable> ref = new AtomicReference<>();
        Disposable disposable = answerStreamer.askStream(req.question(), topK)
                .doOnNext(answer::append)
                .doOnCancel(() -> finished.set(true))
                .subscribe(
                        token -> send(session, new ChatResponse(TOKEN, token, conversationId)),
                        error -> {
                            finished.set(true);
                            log.error("Stream error: conversationId={}, error={}",
                                    conversationId, error.getMessage(), error);
                            send(session, new ChatResponse(ERROR, error.getMessage(), conversationId));
                            removeStream(conversationId, ref);
                        },
                        () -> {
                            if (finished.compareAndSet(false, true)) {
                                log.info("Stream complete: conversationId={}, answerLength={}",
                                        conversationId, answer.length());
                                send(session, new ChatResponse(DONE, answer.toString(), conversationId));
                            } else {
                                log.info("Stream cancelled: conversationId={}", conversationId);
                            }
                            removeStream(conversationId, ref);
                        });
        ref.set(disposable);
        streams.put(conversationId, disposable);
        if (disposable.isDisposed()) {
            streams.remove(conversationId, disposable);
        }
    }

    private void removeStream(String conversationId, AtomicReference<Disposable> ref) {
        var disposable = ref.get();
        if (disposable != null) {
            streams.remove(conversationId, disposable);
        }
    }

    private void cancel(String conversationId) {
        var disposable = streams.remove(conversationId);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_BYTES));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        var conversationId = conversationBySession.remove(session.getId());
        if (conversationId != null) {
            log.info("Connection closed: conversationId={}, status={}", conversationId, status);
            cancel(conversationId);
        }
    }

    private void send(WebSocketSession session, ChatResponse res) {
        try {
            var target = sessions.getOrDefault(session.getId(), session);
            target.sendMessage(new TextMessage(toJson(res)));
        } catch (Exception exception) {
            throw new UncheckedIOException(new IOException(exception));
        }
    }

    private String toJson(ChatResponse res) {
        try {
            return objectMapper.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}