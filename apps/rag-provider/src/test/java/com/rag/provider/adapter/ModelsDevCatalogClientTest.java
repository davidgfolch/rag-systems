package com.rag.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.domain.ModelInfo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers defensive parsing of the models.dev {@code api.json} shape and the
 * HTTP fetch, including offline/error paths.
 */
class ModelsDevCatalogClientTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String SAMPLE = """
            {"ollama":{"name":"Ollama","api":"http://localhost:11434","env":["OLLAMA_API_KEY"],"models":{
              "phi4":{"name":"Phi 4","limit":{"context":16000},"cost":{"input":0.0,"output":0.0},
                      "reasoning":true,"tool_call":false,"structured_output":true},
              "nomic-embed-text":{"name":"Nomic Embed Text","limit":{"context":8192,"output":512},"status":"beta"}}},
             "openai":{"name":"OpenAI","models":{
              "gpt-4o":{"name":"GPT-4o","limit":{"context":128000,"output":16384},
                        "cost":{"input":2.5,"output":10},"reasoning":false,"tool_call":true,"structured_output":false}}},
             "nobody":{"name":"No models"}}
            """;

    @Test
    void shouldParseCatalogEntriesDefensively() throws Exception {
        List<ModelInfo> models = ModelsDevCatalogClient.parse(OM, SAMPLE);

        assertThat(models).hasSize(3);
        var phi4 = find(models, "ollama", "phi4");
        assertThat(phi4.name()).isEqualTo("Phi 4");
        assertThat(phi4.limits().context()).isEqualTo(16000);
        assertThat(phi4.limits().output()).isZero();
        assertThat(phi4.capabilities().reasoning()).isTrue();
        assertThat(phi4.capabilities().toolCall()).isFalse();
        assertThat(phi4.capabilities().structuredOutput()).isTrue();
        assertThat(phi4.cost().inputPerMillion()).isZero();
        assertThat(phi4.status()).isNull();
        assertThat(phi4.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(phi4.apiKeyEnv()).isEqualTo("OLLAMA_API_KEY");

        assertThat(find(models, "ollama", "nomic-embed-text").status()).isEqualTo("beta");
        var gpt4o = find(models, "openai", "gpt-4o");
        assertThat(gpt4o.cost().inputPerMillion()).isEqualTo(2.5);
        assertThat(gpt4o.cost().outputPerMillion()).isEqualTo(10);
        assertThat(gpt4o.limits().context()).isEqualTo(128000);
        assertThat(gpt4o.baseUrl()).isNull();
        assertThat(gpt4o.apiKeyEnv()).isNull();
    }

    @Test
    void shouldFetchCatalogOverHttp() throws Exception {
        var server = server("/api.json", 200, SAMPLE);
        try {
            var client = client(server, "/api.json");

            var models = client.fetch();

            assertThat(models).hasSize(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFailFetchOnHttpError() throws Exception {
        var server = server("/error", 500, "boom");
        try {
            var client = client(server, "/error");

            assertThatThrownBy(client::fetch)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to fetch model catalog");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFailFetchOnMalformedBody() throws Exception {
        var server = server("/broken", 200, "{oops");
        try {
            var client = client(server, "/broken");

            assertThatThrownBy(client::fetch)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to parse model catalog");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnEmptyListOnBlankBody() throws Exception {
        var server = server("/empty", 200, "");
        try {
            var client = client(server, "/empty");

            assertThat(client.fetch()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    private static ModelsDevCatalogClient client(HttpServer server, String path) {
        var restClient = RestClient.builder().baseUrl(baseUrl(server) + path).build();
        return new ModelsDevCatalogClient("test", restClient, OM);
    }

    private static ModelInfo find(List<ModelInfo> models, String providerId, String modelId) {
        return models.stream()
                .filter(m -> m.providerId().equals(providerId) && m.modelId().equals(modelId))
                .findFirst()
                .orElseThrow();
    }

    private static HttpServer server(String path, int status, String body) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }
}