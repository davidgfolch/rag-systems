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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/**
 * Drives the real interactive terminal loop end-to-end against a stub module:
 * a failed ingest (missing file) must not kill the shell, and a successful one
 * must reach the module and print the ingest result.
 */
class InteractiveShellIntegrationTest {

    private StubModuleServer stub;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        ModuleRegistry registry = new ModuleRegistry(
                List.of(new Module("rag-basic", stub.baseUrl())), "rag-basic");
        RagApiClient apiClient = new RagApiClient(registry, RestClient.builder());
        ChatGateway chatGateway = new ChatGateway(
                registry, new org.springframework.web.socket.client.standard.StandardWebSocketClient(), new ObjectMapper());
        MemoryClient memoryClient = new MemoryClient(
                RestClient.builder().baseUrl(stub.baseUrl()).build());
        dispatcher = new CommandDispatcher(registry, mock(ModuleLifecycleManager.class),
                new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient,
                        new FileDocumentLoader(), new ModuleHealthClient(RestClient.builder())),
                new CommandDispatcher.Settings(5_000, 4));
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void ingestsFileAndQuits() throws IOException {
        Path pdf = Files.createTempFile("doc", ".pdf");
        byte[] bytes = "%PDF-1.4 binary".getBytes(StandardCharsets.UTF_8);
        Files.write(pdf, bytes);
        StringWriter out = new StringWriter();

        new InteractiveShell(dispatcher, new StringReader("add-file " + pdf + "\nquit\n"), out).run();

        assertThat(out.toString()).contains("document i-1", "Bye.");
        awaitContains(out, "2 chunks");
        assertThat(out.toString()).contains("2 chunks");
        assertThat(new String(stub.lastIngestBody(), StandardCharsets.UTF_8)).contains(pdf.getFileName().toString());
        assertThat(stub.lastIngestContentType()).startsWith("multipart/form-data");
    }

    private static void awaitContains(StringWriter out, String needle) {
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> out.toString().contains(needle));
    }

    @Test
    void survivesFailedCommandAndContinuesToQuit() {
        StringWriter out = new StringWriter();

        new InteractiveShell(dispatcher,
                new StringReader("add-file definitely-missing.pdf\nhelp\nquit\n"), out).run();

        assertThat(out.toString()).contains("Failed to read file");
        assertThat(out.toString()).contains("Available commands");
        assertThat(out.toString()).contains("Bye.");
        assertThat(out.toString()).doesNotContain("Exception");
    }
}