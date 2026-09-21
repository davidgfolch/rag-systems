package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.tracing.TracePropagation;
import com.rag.contract.constants.ApiPaths;
import com.rag.contract.provider.CompleteRequest;
import com.rag.contract.provider.CompleteResponse;
import com.rag.contract.provider.ProviderStatusDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.test.simple.SimpleTraceContext;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderHttpClientTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_ID = "0123456789abcdef";

    private HttpServer server;
    private ProviderHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        client = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldPostJsonAndParse() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "{\"answer\":\"Hi\"}"));
        var answer = client.postJson(ApiPaths.COMPLETE, new CompleteRequest("hi"), CompleteResponse.class);
        assertThat(answer.answer()).isEqualTo("Hi");
    }

    @Test
    void shouldGetJsonAndParse() {
        server.createContext(ApiPaths.PROVIDER,
                exchange -> respond(exchange, 200, "application/json",
                        "{\"chat\":{\"providerId\":\"o\",\"model\":\"m\"},\"embedding\":{\"providerId\":\"o\",\"model\":\"em\"},\"provider\":\"o\",\"embeddingDimension\":768}"));
        var status = client.get(ApiPaths.PROVIDER, ProviderStatusDTO.class);
        assertThat(status.embeddingDimension()).isEqualTo(768);
    }

    @Test
    void shouldThrowOnServerError() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 500, "text/plain", "boom"));
        var request = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postJson(ApiPaths.COMPLETE, request, CompleteResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void shouldThrowOnServerErrorFromGet() {
        server.createContext(ApiPaths.PROVIDER,
                exchange -> respond(exchange, 500, "text/plain", "boom"));
        assertThatThrownBy(() -> client.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void shouldThrowOnMalformedResponse() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "not-json"));
        var request = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postJson(ApiPaths.COMPLETE, request, CompleteResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldThrowOnMalformedResponseFromGet() {
        server.createContext(ApiPaths.PROVIDER,
                exchange -> respond(exchange, 200, "application/json", "not-json"));
        assertThatThrownBy(() -> client.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldThrowOnStreamErrorStatus() {
        server.createContext(ApiPaths.CHAT_STREAM,
                exchange -> respond(exchange, 500, "text/plain", "boom"));
        var req = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postStream(ApiPaths.CHAT_STREAM, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider stream failed: HTTP 500");
    }

    @Test
    void shouldThrowWhenTransportFails() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("refused"));
        var failing = new ProviderHttpClient("http://localhost:1", httpClient, new ObjectMapper());
        assertThatThrownBy(() -> failing.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider request failed");
        Thread.interrupted();
    }

    @Test
    void shouldFailStreamRequestWhenTransportFails() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("refused"));
        var failing = new ProviderHttpClient("http://localhost:1", httpClient, new ObjectMapper());
        var req = new CompleteRequest("hi");
        assertThatThrownBy(() -> failing.postStream(ApiPaths.CHAT_STREAM, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider stream request failed");
        Thread.interrupted();
    }

    @Test
    void shouldDescribeUnknownExceptionsByClassName() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("boom", new RuntimeException("root")));
        var failing = new ProviderHttpClient("http://localhost:1", httpClient, new ObjectMapper());
        var error = org.assertj.core.api.Assertions.catchThrowable(
                () -> failing.get(ApiPaths.PROVIDER, ProviderStatusDTO.class));
        assertThat(error).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void shouldDescribeNullMessageExceptionsByClassName() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException());
        var failing = new ProviderHttpClient("http://localhost:1", httpClient, new ObjectMapper());
        assertThatThrownBy(() -> failing.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IOException");
        Thread.interrupted();
    }

    @Test
    void shouldSupportInjectedHttpClientConstructor() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("refused"));
        var injected = new ProviderHttpClient("http://localhost:1", httpClient, new ObjectMapper());
        assertThatThrownBy(() -> injected.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class);
        Thread.interrupted();
    }

    @Test
    void shouldOpenStreamForPost() throws IOException {
        server.createContext(ApiPaths.CHAT_STREAM,
                exchange -> respond(exchange, 200, "text/event-stream", "data:{}\n\n"));
        try (var body = client.postStream(ApiPaths.CHAT_STREAM, new CompleteRequest("hi"))) {
            assertThat(body).isNotNull();
        }
    }

    @Test
    void shouldAttachW3CTraceparentWhenSpanActive() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst(TracePropagation.TRACEPARENT_HEADER));
            respond(exchange, 200, "application/json", "{\"answer\":\"Hi\"}");
        });
        var tracer = new SimpleTracer();
        var traced = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper(), tracer);
        var span = tracer.nextSpan().name("test-call").start();
        var context = (SimpleTraceContext) span.context();
        context.setTraceId(TRACE_ID);
        context.setSpanId(SPAN_ID);
        context.setSampled(true);
        try (var _ = tracer.withSpan(span)) {
            traced.postJson(ApiPaths.COMPLETE, new CompleteRequest("hi"), CompleteResponse.class);
        }
        assertThat(traceparent.get()).isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
    }

    @Test
    void shouldSkipTraceparentWhenSpanIdsNotW3CConformant() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst(TracePropagation.TRACEPARENT_HEADER));
            respond(exchange, 200, "application/json", "{\"answer\":\"Hi\"}");
        });
        var tracer = new SimpleTracer();
        var traced = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper(), tracer);
        var span = tracer.nextSpan().name("test-call").start();
        try (var _ = tracer.withSpan(span)) {
            traced.postJson(ApiPaths.COMPLETE, new CompleteRequest("hi"), CompleteResponse.class);
        }
        assertThat(traceparent.get()).isNull();
    }

    @Test
    void shouldSkipTraceparentWhenNoSpanActive() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst(TracePropagation.TRACEPARENT_HEADER));
            respond(exchange, 200, "application/json", "{\"answer\":\"Hi\"}");
        });
        var traced = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper(), new SimpleTracer());
        traced.postJson(ApiPaths.COMPLETE, new CompleteRequest("hi"), CompleteResponse.class);
        assertThat(traceparent.get()).isNull();
    }

    @Test
    void shouldSkipTraceparentWithoutTracer() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst(TracePropagation.TRACEPARENT_HEADER));
            respond(exchange, 200, "application/json", "{\"answer\":\"Hi\"}");
        });
        client.postJson(ApiPaths.COMPLETE, new CompleteRequest("hi"), CompleteResponse.class);
        assertThat(traceparent.get()).isNull();
    }

    @Test
    void shouldRejectBlankBaseUrl() {
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> new ProviderHttpClient("  ", mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTrimTrailingSlashFromBaseUrl() {
        var trimmed = new ProviderHttpClient("http://localhost:8086/", new ObjectMapper());
        assertThat(trimmed).isNotNull();
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