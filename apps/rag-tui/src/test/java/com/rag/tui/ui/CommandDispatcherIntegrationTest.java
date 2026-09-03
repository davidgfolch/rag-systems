package com.rag.tui.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.services.FileDocumentLoader;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end flow through the real command dispatcher: a real module stub over
 * HTTP, a real {@link FileDocumentLoader} reading a temp file, and the real
 * REST/health/memory clients. No HTTP mocking, no Spring context.
 */
class CommandDispatcherIntegrationTest {

    private StubModuleServer stub;
    private ModuleRegistry registry;
    private ModuleLifecycleManager lifecycle;
    private CommandDispatcher sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        registry = new ModuleRegistry(
                List.of(new Module("rag-basic", stub.baseUrl())), "rag-basic");
        lifecycle = mock(ModuleLifecycleManager.class);
        RagApiClient apiClient = new RagApiClient(registry, RestClient.builder());
        ChatGateway chatGateway = new ChatGateway(
                registry, new org.springframework.web.socket.client.standard.StandardWebSocketClient(), new ObjectMapper());
        MemoryClient memoryClient = new MemoryClient(
                RestClient.builder().baseUrl(stub.baseUrl()).build());
        sut = new CommandDispatcher(registry, lifecycle,
                new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient,
                        new FileDocumentLoader(), new ModuleHealthClient(RestClient.builder())),
                new CommandDispatcher.Settings(5_000, 4));
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void ingestsBinaryFileOverHttpViaMultipart() throws IOException {
        Path pdf = Files.createTempFile("doc", ".pdf");
        byte[] bytes = "%PDF-1.4 fake binary content \u0000\u0001\u0002".getBytes(StandardCharsets.UTF_8);
        Files.write(pdf, bytes);
        List<String> tokens = new java.util.ArrayList<>();

        CommandResult result = sut.handle("add-file " + pdf, tokens::add);

        assertThat(result.message()).contains("submitted", "i-1");
        await(tokens, "2 chunks");
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("2 chunks"));
        assertThat(stub.lastIngestContentType()).startsWith("multipart/form-data");
        String body = new String(stub.lastIngestBody(), StandardCharsets.UTF_8);
        assertThat(body).contains(pdf.getFileName().toString());
    }

    private static void await(List<String> tokens, String needle) {
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    @Test
    void readsHistoryFromStubbedModule() {
        CommandResult history = sut.handle("history", token -> {});

        assertThat(history.message()).contains("No conversations yet");
        assertThat(history.exit()).isFalse();
    }

    @Test
    void reportsUnreachableModuleInsteadOfCrashing() throws IOException {
        ModuleRegistry deadRegistry = new ModuleRegistry(
                List.of(new Module("rag-basic", "http://localhost:1")), "rag-basic");
        ChatGateway chatGateway = new ChatGateway(
                deadRegistry, new org.springframework.web.socket.client.standard.StandardWebSocketClient(), new ObjectMapper());
        CommandDispatcher dead = new CommandDispatcher(deadRegistry, lifecycle,
                new CommandDispatcher.RagClients(
                        new RagApiClient(deadRegistry, RestClient.builder()), chatGateway,
                        mock(MemoryClient.class), new FileDocumentLoader(),
                        new ModuleHealthClient(RestClient.builder())),
                new CommandDispatcher.Settings(1_000, 4));

        CommandResult result = dead.handle("add-file " + testFile(), token -> {});

        assertThat(result.message()).contains("Module unreachable");
        assertThat(result.exit()).isFalse();
    }

    @Test
    void startWaitsForModuleToBecomeHealthy() {
        when(lifecycle.start(registry.active())).thenReturn(true);
        when(lifecycle.isRunning("rag-basic")).thenReturn(true);

        CommandResult result = sut.handle("start rag-basic", token -> {});

        assertThat(result.message()).contains("ready");
    }

    private String testFile() throws IOException {
        Path pdf = Files.createTempFile("doc", ".pdf");
        Files.write(pdf, new byte[]{0x25, 0x50, 0x44, 0x46});
        return pdf.toString();
    }
}