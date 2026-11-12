package com.rag.common.generation.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.contract.constants.ApiPaths;
import com.rag.contract.provider.CompleteRequest;
import com.rag.contract.provider.CompleteResponse;
import com.rag.contract.provider.ProviderStatusDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderHttpClientRetryTest {

    private static final String ANSWER_JSON = "{\"answer\":\"Hi\"}";
    private static final int FAST_RETRY_ATTEMPTS = 3;
    private static final ProviderHttpSettings FAST_RETRY = new ProviderHttpSettings(
            Duration.ofSeconds(1), Duration.ofSeconds(2),
            new RetryPolicy(FAST_RETRY_ATTEMPTS, Duration.ofMillis(1), Duration.ofMillis(1), 2.0, 0));

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldRetryTransientServerErrorThenSucceed() {
        var calls = new AtomicInteger();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            if (calls.incrementAndGet() < FAST_RETRY_ATTEMPTS) {
                respond(exchange, 503, "text/plain", "busy");
            } else {
                respond(exchange, 200, "application/json", ANSWER_JSON);
            }
        });

        var answer = resilientClient().postJson(ApiPaths.COMPLETE,
                new CompleteRequest("hi"), CompleteResponse.class);

        assertThat(answer.answer()).isEqualTo("Hi");
        assertThat(calls).hasValue(FAST_RETRY_ATTEMPTS);
    }

    @Test
    void shouldGiveUpAfterRetriesAreExhausted() {
        var calls = new AtomicInteger();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "text/plain", "busy");
        });

        var client = resilientClient();
        var req = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postJson(ApiPaths.COMPLETE, req, CompleteResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 503");
        assertThat(calls).hasValue(FAST_RETRY_ATTEMPTS);
    }

    @Test
    void shouldRetryRateLimitedResponse() {
        var calls = new AtomicInteger();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "0");
            if (calls.incrementAndGet() < FAST_RETRY_ATTEMPTS) {
                respond(exchange, 429, "text/plain", "slow down");
            } else {
                respond(exchange, 200, "application/json", ANSWER_JSON);
            }
        });

        var answer = resilientClient().postJson(ApiPaths.COMPLETE,
                new CompleteRequest("hi"), CompleteResponse.class);

        assertThat(answer.answer()).isEqualTo("Hi");
        assertThat(calls).hasValue(FAST_RETRY_ATTEMPTS);
    }

    @Test
    void shouldNotRetryClientErrors() {
        var calls = new AtomicInteger();
        server.createContext(ApiPaths.COMPLETE, exchange -> {
            calls.incrementAndGet();
            respond(exchange, 400, "text/plain", "bad request");
        });

        var client = resilientClient();
        var req = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postJson(ApiPaths.COMPLETE, req, CompleteResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 400");
        assertThat(calls).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRetryTransportTimeoutThenSucceed() throws Exception {
        var httpClient = mock(HttpClient.class);
        var res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(200);
        when(res.body()).thenReturn(ANSWER_JSON);
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException("timeout"))
                .thenReturn(res);
        var retrying = new ProviderHttpClient("http://localhost:1", httpClient,
                new ObjectMapper(), null, FAST_RETRY, RetryObserver.NOOP);

        var answer = retrying.postJson(ApiPaths.COMPLETE,
                new CompleteRequest("hi"), CompleteResponse.class);

        assertThat(answer.answer()).isEqualTo("Hi");
    }

    @Test
    void shouldNotRetryInterruptedRequests() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("halt"));
        var retrying = new ProviderHttpClient("http://localhost:1", httpClient,
                new ObjectMapper(), null, FAST_RETRY, RetryObserver.NOOP);

        assertThatThrownBy(() -> retrying.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private ProviderHttpClient resilientClient() {
        return new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper(), null, FAST_RETRY, RetryObserver.NOOP);
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
