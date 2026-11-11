package com.rag.webcrawler.services.discovery;

import com.rag.common.core.resilience.RetryPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpUrlContentLoaderRetryTest {

    private static final Duration FAST = Duration.ofMillis(1);
    private static final RetryPolicy FAST_RETRY = new RetryPolicy(3, FAST, FAST, 2.0, 0);

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        Thread.interrupted();
    }

    @Test
    void shouldRetryTransientStatusThenSucceed() {
        var calls = new AtomicInteger();
        server.createContext("/sitemap.xml", exchange -> {
            int call = calls.incrementAndGet();
            respond(exchange, call == 1 ? 503 : 200, call == 1 ? "busy" : "<urlset/>");
        });
        var sut = new HttpUrlContentLoader(HttpClient.newHttpClient(), FAST_RETRY);

        assertThat(sut.load(baseUrl + "/sitemap.xml"))
                .hasValueSatisfying(bytes ->
                        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("<urlset/>"));
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldNotRetryClientErrorStatus() {
        var calls = new AtomicInteger();
        server.createContext("/missing.txt", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 404, "");
        });
        var sut = new HttpUrlContentLoader(HttpClient.newHttpClient(), FAST_RETRY);

        assertThat(sut.load(baseUrl + "/missing.txt")).isEmpty();
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldRetryTransientTransportFailure() throws Exception {
        var client = mock(HttpClient.class);
        var res = response(200, "ok".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), bodyHandler()))
                .thenThrow(new ConnectException("refused"))
                .thenReturn(res);
        var sut = new HttpUrlContentLoader(client, FAST_RETRY);

        assertThat(sut.load(baseUrl + "/robots.txt"))
                .hasValueSatisfying(bytes ->
                        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("ok"));
        verify(client, times(2)).send(any(HttpRequest.class), bodyHandler());
    }

    @Test
    void shouldNotRetryPermanentIoFailure() throws Exception {
        var client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), bodyHandler()))
                .thenThrow(new IOException("broken pipe"));
        var sut = new HttpUrlContentLoader(client, FAST_RETRY);

        assertThat(sut.load(baseUrl + "/robots.txt")).isEmpty();
        verify(client, times(1)).send(any(HttpRequest.class), bodyHandler());
    }

    @Test
    void shouldRestoreInterruptAndNotRetry() throws Exception {
        var client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), bodyHandler()))
                .thenThrow(new InterruptedException("halt"));
        var sut = new HttpUrlContentLoader(client, FAST_RETRY);

        assertThat(sut.load(baseUrl + "/robots.txt")).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(client, times(1)).send(any(HttpRequest.class), bodyHandler());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<byte[]> bodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, byte[] body) {
        HttpResponse<byte[]> res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(status);
        when(res.body()).thenReturn(body);
        return res;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
