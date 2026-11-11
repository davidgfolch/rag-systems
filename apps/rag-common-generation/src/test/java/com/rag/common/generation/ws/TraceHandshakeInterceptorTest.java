package com.rag.common.generation.ws;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceHandshakeInterceptorTest {

    private final Tracer tracer = mock(Tracer.class);
    private final ServerHttpRequest req = mock(ServerHttpRequest.class);
    private final ServerHttpResponse res = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);
    private final TraceHandshakeInterceptor interceptor = new TraceHandshakeInterceptor(tracer);

    @Test
    void capturesCurrentSpanIntoAttributes() {
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor.beforeHandshake(req, res, wsHandler, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes)
                .containsEntry(ChatWebSocketHandler.SPAN_ATTRIBUTE, span);
    }

    @Test
    void proceedsWithoutSpanWhenNoActiveTrace() {
        when(tracer.currentSpan()).thenReturn(null);
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor.beforeHandshake(req, res, wsHandler, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes).doesNotContainKey(ChatWebSocketHandler.SPAN_ATTRIBUTE);
    }

    @Test
    void afterHandshakeDoesNotInterfereWithSubsequentHandshakes() {
        interceptor.afterHandshake(req, res, wsHandler, null);
        interceptor.afterHandshake(req, res, wsHandler, new IllegalStateException("boom"));

        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        Map<String, Object> attributes = new HashMap<>();
        boolean proceed = interceptor.beforeHandshake(req, res, wsHandler, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes).containsEntry(ChatWebSocketHandler.SPAN_ATTRIBUTE, span);
    }
}