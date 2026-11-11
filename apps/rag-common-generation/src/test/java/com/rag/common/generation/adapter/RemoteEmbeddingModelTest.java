package com.rag.common.generation.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.generation.testfixture.TestProviderHttpClients;
import com.rag.contract.constants.ApiPaths;
import com.rag.contract.provider.ProviderStatusDTO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteEmbeddingModelTest {

    private static final String STATUS_JSON = """
            {"chat":{"providerId":"ollama","model":"phi4"},
             "embedding":{"providerId":"ollama","model":"nomic-embed-text"},
             "provider":"ollama","embeddingDimension":768}""";

    private HttpServer server;
    private RemoteEmbeddingModel model;
    private final AtomicInteger providerStatusCalls = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicReference<String> lastEmbedBody =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        var client = TestProviderHttpClients.of("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper());
        model = new RemoteEmbeddingModel(client, 768);
        server.createContext(ApiPaths.PROVIDER, exchange -> {
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
        server.createContext(ApiPaths.EMBED,
                exchange -> respond(exchange, 200, "application/json",
                        "{\"embeddings\":[[0.0,1.0],[2.0,3.0]]}"));
        var res = model.call(new EmbeddingRequest(List.of("a", "b"), null));
        assertThat(res.getResults()).hasSize(2);
        assertThat(res.getResult().getOutput()).containsExactly(0.0f, 1.0f);
    }

    @Test
    void shouldEmbedDocumentViaApiEmbed() {
        server.createContext(ApiPaths.EMBED,
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

    @Test
    void shouldSendTheVariantModelAndProbeItsDimension() {
        server.createContext(ApiPaths.EMBED, exchange -> {
            lastEmbedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "application/json", "{\"embeddings\":[[1.0,2.0,3.0]]}");
        });
        var client = TestProviderHttpClients.of("http://localhost:" + server.getAddress().getPort(),
                new ObjectMapper());
        var sut = new RemoteEmbeddingModel(client, 0, "mxbai-embed-large");
        assertThat(sut.embed(new Document("text"))).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(lastEmbedBody.get()).contains("\"model\":\"mxbai-embed-large\"");
        assertThat(sut.dimensions()).isEqualTo(3);
        assertThat(providerStatusCalls.get()).isZero();
    }

    @Test
    void shouldNotSendAModelForTheActiveModel() {
        server.createContext(ApiPaths.EMBED, exchange -> {
            lastEmbedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "application/json", "{\"embeddings\":[[1.0]]}");
        });
        model.embed(new Document("text"));
        assertThat(lastEmbedBody.get()).doesNotContain("\"model\":\"");
    }

    @Test
    void shouldFallBackToDefaultDimensionWhenProviderUnreachableAndRetryLater() throws IOException {
        var down = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        down.start();
        var client = TestProviderHttpClients.of("http://localhost:" + down.getAddress().getPort(),
                new ObjectMapper());
        var sut = new RemoteEmbeddingModel(client, 512);
        assertThat(sut.dimensions()).isEqualTo(512);
        down.createContext(ApiPaths.PROVIDER, exchange ->
                respond(exchange, 200, "application/json", STATUS_JSON));
        assertThat(sut.dimensions()).isEqualTo(768);
        down.stop(0);
    }

    @Test
    void shouldRethrowWhenNoFallbackDimensionConfigured() {
        var client = mock(ProviderHttpClient.class);
        when(client.get(ApiPaths.PROVIDER, ProviderStatusDTO.class))
                .thenThrow(new IllegalStateException("down"));
        var sut = new RemoteEmbeddingModel(client, 0);
        assertThatThrownBy(sut::dimensions)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("down");
    }

    @Test
    void shouldDeferDimensionFetchWhenStatusUnavailableThenResolve() throws IOException {
        var srv = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        srv.start();
        AtomicInteger statusCalls = new AtomicInteger();
        srv.createContext(ApiPaths.PROVIDER, exchange -> {
            if (statusCalls.incrementAndGet() == 1) {
                respond(exchange, 500, "text/plain", "down");
            } else {
                respond(exchange, 200, "application/json", STATUS_JSON);
            }
        });
        srv.createContext(ApiPaths.EMBED, exchange ->
                respond(exchange, 200, "application/json", "{\"embeddings\":[[0.0,1.0]]}"));
        var client = TestProviderHttpClients.of("http://localhost:" + srv.getAddress().getPort(),
                new ObjectMapper());
        var sut = new RemoteEmbeddingModel(client, 512);
        var res = sut.call(new EmbeddingRequest(List.of("a"), null));
        assertThat(res.getResult().getOutput()).containsExactly(0.0f, 1.0f);
        sut.call(new EmbeddingRequest(List.of("a"), null));
        assertThat(sut.dimensions()).isEqualTo(768);
        srv.stop(0);
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