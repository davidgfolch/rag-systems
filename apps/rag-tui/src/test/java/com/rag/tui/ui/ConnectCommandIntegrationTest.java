package com.rag.tui.ui;

import com.rag.tui.client.ProviderClient;
import com.rag.tui.support.StubProviderServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectCommandIntegrationTest {

    private StubProviderServer server;
    private ConnectCommand sut;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubProviderServer();
        sut = new ConnectCommand(new ProviderClient(server.baseUrl(), RestClient.builder()),
                new NoopPrompter(new java.io.StringReader("")));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void rendersProviderStatus() {
        var result = sut.execute("");

        assertThat(result)
                .contains("chat: ollama/phi4", "embedding: ollama/nomic-embed-text", "dimension 768")
                .contains("1 models");
    }

    @Test
    void listsCatalog() {
        var result = sut.execute("catalog");

        assertThat(result)
                .contains("ollama:")
                .contains("- phi4 (Phi-4)", "ctx 16384")
                .contains("reasoning");
    }

    @Test
    void switchesChatModel() {
        var result = sut.execute("chat ollama phi4");

        assertThat(result).contains("Chat model switched", "ollama/phi4");
        assertThat(server.lastSwitchBody()).contains("\"providerId\":\"ollama\"");
    }

    @Test
    void refreshesCatalog() {
        var result = sut.execute("refresh");

        assertThat(result).contains("Catalog refreshed", "1 models");
    }
}