package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteEmbeddingModelTest {

    private static final String STATUS_JSON = """
            {"chat":{"providerId":"ollama","model":"phi4"},
             "embedding":{"providerId":"ollama","model":"nomic-embed-text"},
             "provider":"ollama","embeddingDimension":768}""";

    private HttpServer server;
    private RemoteEmbeddingModel model;
    private final AtomicInteger providerStatusCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        var client = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper());
        model = new RemoteEmbeddingModel(client);
        server.createContext("/api/provider", exchange -> {
            providerStatusCalls.incrementAndGet();
            respond(exchange, 200, "application/json", STATUS_JSON);
        });
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldResolveDimensionFromStatus() {
        assertThat(model.dimensions()).isEqualTo(768);
        assertThat(providerStatusCalls.get()).isEqualTo(1);
    }

    @Test
    void shouldEmbedTextsViaApiEmbed() {
        server.createContext("/api/embed",
                exchange -> respond(exchange, 200, "application/json",
                        "{\"embeddings\":[[0.0,1.0],[2.0,3.0]]}"));

        var response = model.call(new EmbeddingRequest(List.of("a", "b"), null));

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResult().getOutput()).containsExactly(0.0f, 1.0f);
    }

    @Test
    void shouldEmbedDocumentViaApiEmbed() {
        server.createContext("/api/embed",
                exchange -> respond(exchange, 200, "application/json",
                        "{\"embeddings\":[[4.0,5.0]]}"));

        assertThat(model.embed(new Document("text"))).containsExactly(4.0f, 5.0f);
    }

    @Test
    void shouldCacheDimensionAcrossCalls() {
        model.dimensions();
        model.dimensions();
        assertThat(model.dimensions()).isEqualTo(768);
        assertThat(providerStatusCalls.get()).isEqualTo(1);
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