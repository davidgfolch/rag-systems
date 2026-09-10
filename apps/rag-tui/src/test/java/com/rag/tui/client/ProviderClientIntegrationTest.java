package com.rag.tui.client;

import com.rag.tui.support.StubProviderServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderClientIntegrationTest {

    private StubProviderServer server;
    private ProviderClient sut;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubProviderServer();
        sut = new ProviderClient(server.baseUrl(), RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void loadsProviderStatusOverHttp() {
        var status = sut.status();
        assertThat(status.chat().providerId()).isEqualTo("ollama");
        assertThat(status.chat().model()).isEqualTo("phi4");
        assertThat(status.embedding().model()).isEqualTo("nomic-embed-text");
        assertThat(status.embeddingDimension()).isEqualTo(768);
    }

    @Test
    void loadsCatalogOverHttp() {
        var catalog = sut.catalog();
        assertThat(catalog.models()).hasSize(1);
        assertThat(catalog.models().getFirst().modelId()).isEqualTo("phi4");
        assertThat(catalog.source()).isEqualTo("https://models.dev/api.json");
        assertThat(catalog.fetchedAt()).isNotNull();
    }

    @Test
    void refreshesCatalogOverHttp() {
        var catalog = sut.refreshCatalog();
        assertThat(catalog.models()).hasSize(1);
    }

    @Test
    void switchesChatModelOverHttp() {
        sut.switchChat("ollama", "phi4");
        assertThat(server.lastSwitchBody()).contains("\"providerId\":\"ollama\"", "\"model\":\"phi4\"");
    }

    @Test
    void switchesEmbeddingModelOverHttp() {
        sut.switchEmbedding("ollama", "nomic-embed-text");
        assertThat(server.lastSwitchBody()).contains("\"providerId\":\"ollama\"", "\"model\":\"nomic-embed-text\"");
    }
}