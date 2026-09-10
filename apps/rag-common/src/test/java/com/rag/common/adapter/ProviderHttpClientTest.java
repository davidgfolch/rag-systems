package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderHttpClientTest {

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
    void shouldThrowOnMalformedResponse() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "not-json"));
        var request = new CompleteRequest("hi");
        assertThatThrownBy(() -> client.postJson(ApiPaths.COMPLETE, request, CompleteResponse.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
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