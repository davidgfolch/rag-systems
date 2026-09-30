package com.rag.common.generation.ws;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the active trace span during the WebSocket handshake so the message
 * handling thread can re-scope it ({@link ChatWebSocketHandler}) and keep the
 * outbound provider calls inside the same trace as the TUI command that opened
 * the socket.
 */
public class TraceHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TraceHandshakeInterceptor.class);

    private final Tracer tracer;

    public TraceHandshakeInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        var span = tracer.currentSpan();
        if (span != null) {
            attributes.put(ChatWebSocketHandler.SPAN_ATTRIBUTE, span);
            log.debug("Captured current span {} for WebSocket handshake", span);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    // Intentionally empty: the Span is captured during beforeHandshake.
    }
}