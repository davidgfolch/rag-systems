package com.rag.tui.ui;

import com.rag.tui.client.ProviderClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubProviderServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link CommandCompletion} against a real {@link ProviderClient}
 * hitting the stub rag-provider server: the candidates pipeline runs for real,
 * only the HTTP backing is stubbed.
 */
class CommandCompletionIntegrationTest {

    private StubProviderServer server;
    private CommandCompletion sut;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubProviderServer();
        sut = new CommandCompletion(new CommandRegistry(),
                new ModuleRegistry(List.of(new Module("rag-basic", "http://localhost:8081")), "rag-basic"),
                new ProviderClient(server.baseUrl(), RestClient.builder()));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void showsProviderWithMatchingPrefixInConnectContext() {
        var result = sut.candidates("connect o", 9);
        assertThat(result).contains("ollama");
    }

    @Test
    void showsModelsForSelectedProvider() {
        var result = sut.candidates("connect chat ollama p", 21);
        assertThat(result).contains("phi4");
    }
}