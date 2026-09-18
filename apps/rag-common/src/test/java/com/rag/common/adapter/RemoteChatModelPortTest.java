package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.tracing.TracePropagation;
import com.rag.contract.constants.ApiPaths;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteChatModelPortTest {

    private HttpServer server;
    private RemoteChatModelPort port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        var mapper = new ObjectMapper();
        var client = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(), mapper);
        port = new RemoteChatModelPort(client, mapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldCompleteViaApiComplete() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "{\"answer\":\"Hello!\"}"));
        assertThat(port.complete("hi")).isEqualTo("Hello!");
    }

    @Test
    void shouldReturnNullWhenAnswerMissing() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "{\"answer\":null}"));
        assertThat(port.complete("hi")).isNull();
    }

    @Test
    void shouldThrowOnServerError() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 500, "text/plain", "boom"));
        assertThatThrownBy(() -> port.complete("hi")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldStreamTokensAndCompleteOnDone() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                """
                        data:{"type":"token","content":"Hel"}
                        data:{"type":"token","content":"lo"}
                        data:{"type":"done","content":null,"conversationId":null}
                        """));
        StepVerifier.create(port.completeStream("hi"))
                .expectNext("Hel", "lo")
                .verifyComplete();
    }

    @Test
    void shouldCompleteWhenStreamEndsWithoutDoneFrame() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                "data:{\"type\":\"token\",\"content\":\"only\"}"));
        StepVerifier.create(port.completeStream("hi"))
                .expectNext("only")
                .verifyComplete();
    }

    @Test
    void shouldIgnoreNonDataLinesAndUnknownFrames() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                """
                        event: message
                        
                        data:{"type":"mystery","content":"ignored"}
                        data:{"type":"done","content":null,"conversationId":null}
                        """));
        StepVerifier.create(port.completeStream("hi"))
                .verifyComplete();
    }

    @Test
    void shouldSkipTokensWithNullContent() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                """
                        data:{"type":"token","content":null}
                        data:{"type":"done","content":null,"conversationId":null}
                        """));
        StepVerifier.create(port.completeStream("hi")).verifyComplete();
    }

    @Test
    void shouldFailOnProviderErrorFrame() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                "data:{\"type\":\"error\",\"content\":\"nope\",\"conversationId\":null}\n\n"));
        StepVerifier.create(port.completeStream("hi"))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("nope"))
                .verify();
    }

    @Test
    void shouldFailOnMalformedFrame() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                "data:not-json\n\n"));
        StepVerifier.create(port.completeStream("hi"))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("Provider stream failed"))
                .verify();
    }

    @Test
    void shouldPropagateCallerSpanToStreamRequest() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst(TracePropagation.TRACEPARENT_HEADER));
            respond(exchange, 200, "text/event-stream",
                    "data:{\"type\":\"done\",\"content\":null,\"conversationId\":null}\n\n");
        });
        var tracer = new SimpleTracer();
        var tracedClient = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper(), tracer);
        var tracedPort = new RemoteChatModelPort(tracedClient, new ObjectMapper(), tracer);
        var span = tracer.nextSpan().name("test-stream").start();
        try (var _ = tracer.withSpan(span)) {
            StepVerifier.create(tracedPort.completeStream("hi")).verifyComplete();
        }
        assertThat(traceparent.get())
                .matches("^00-" + span.context().traceId() + "-[0-9a-f]{16}-[01]{2}$");
    }

    private static void respond(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}