package com.rag.advanced.config;

import com.rag.advanced.api.chat.ChatWebSocketHandler;
import io.micrometer.tracing.Tracer;
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
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    // Intentionally empty: the Span is captured during beforeHandshake.
    }
}