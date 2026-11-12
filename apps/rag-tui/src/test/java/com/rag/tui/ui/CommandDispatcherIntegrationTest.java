package com.rag.tui.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.services.FileDocumentLoader;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import java.security.MessageDigest;

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
    private CommandDispatcher.RagClients clients;
    private CommandDispatcher.Settings settings;
    private CommandDispatcher sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        registry = new ModuleRegistry(
                List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        lifecycle = mock(ModuleLifecycleManager.class);
        RagApiClient apiClient = new RagApiClient(registry, RestClient.builder(), RestClient.builder());
        ChatGateway chatGateway = new ChatGateway(
                registry, new StandardWebSocketClient(),
                new ObjectMapper(), 60, null);
        MemoryClient memoryClient = new MemoryClient(
                RestClient.builder().baseUrl(stub.baseUrl()).build());
        clients = new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient,
                new FileDocumentLoader(), new ModuleHealthClient(RestClient.builder()),
                new ProviderClient(stub.baseUrl(), RestClient.builder()));
        settings = new CommandDispatcher.Settings(5_000, 4, 60);
        sut = new CommandDispatcher(registry, lifecycle, clients, settings, new CommandRegistry(),
                new NoopPrompter(new StringReader("")), null);
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
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file " + pdf, tokens::add);
        assertThat(result).contains("submitted", "i-1");
        await(tokens, "2 chunks");
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("2 chunks"));
        assertThat(stub.lastIngestContentType()).startsWith("multipart/form-data");
        String body = new String(stub.lastIngestBody(), StandardCharsets.UTF_8);
        assertThat(body).contains(pdf.getFileName().toString());
    }

    @Test
    void addFileDeduplicatesByIdenticalContentOverHttp() throws Exception {
        Path txt = Files.createTempFile("dup", ".txt");
        byte[] bytes = "identical rag content".getBytes(StandardCharsets.UTF_8);
        Files.write(txt, bytes);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        stub.documents("[{\"documentId\":\"existing-1\",\"title\":\"old.txt\",\"chunkCount\":3,"
                + "\"metadata\":{\"contentHash\":\"" + hash + "\"}}]");
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file " + txt, tokens::add);
        assertThat(result).contains("already ingested", "existing-1");
        assertThat(stub.lastIngestBody()).isNull();
    }

    @Test
    void addUrlSkipsExistingPageWithoutOverrideOverHttp() {
        stub.documents("[{\"documentId\":\"web-1\",\"title\":\"Old page\",\"chunkCount\":5,"
                + "\"metadata\":{\"source\":\"https://example.com/guide\"}}]");
        stub.crawlStart("{\"sessionId\":\"c-1\",\"page\":{\"url\":\"https://example.com/guide\","
                + "\"title\":\"Guide\"},\"initialRemaining\":0,\"hasMore\":false}");
        stub.crawlSteps("{\"sessionId\":\"c-1\",\"pageUrl\":\"https://example.com/guide\","
                + "\"title\":\"Guide\",\"ingested\":false,\"initialRemaining\":0,"
                + "\"hasMore\":false,\"skippedLinks\":0}");
        sut = new CommandDispatcher(registry, lifecycle, clients, settings, new CommandRegistry(),
                new NoopPrompter(new StringReader("\n")), null);
        var result = sut.handle("add-url https://example.com/guide", token -> {});
        assertThat(result).contains("Crawl finished", "1 page(s) skipped");
        assertThat(stub.lastDeletedDocumentId()).isNull();
        assertThat(stub.lastCrawlStepBody()).contains("\"override\":false");
    }

    private static void await(List<String> tokens, String needle) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    @Test
    void readsHistoryFromStubbedModule() {
        var history = sut.handle("history", token -> {});
        assertThat(history).contains("No conversations yet");
    }

    @Test
    void reportsUnreachableModuleInsteadOfCrashing() throws IOException {
        ModuleRegistry deadRegistry = new ModuleRegistry(
                List.of(TestModules.withUrl("http://localhost:1")), TestModules.BASIC);
        ChatGateway chatGateway = new ChatGateway(
                deadRegistry, new StandardWebSocketClient(), new ObjectMapper(), 60, null);
        CommandDispatcher dead = new CommandDispatcher(deadRegistry, lifecycle,
                new CommandDispatcher.RagClients(
                        new RagApiClient(deadRegistry, RestClient.builder(), RestClient.builder()), chatGateway,
                        mock(MemoryClient.class), new FileDocumentLoader(),
                        new ModuleHealthClient(RestClient.builder()),
                        new ProviderClient("http://localhost:1", RestClient.builder())),
                new CommandDispatcher.Settings(1_000, 4, 60), new CommandRegistry(),
                new NoopPrompter(new StringReader("")), null);
        var result = dead.handle("add-file " + testFile(), token -> {});
        assertThat(result).contains("Module unreachable");
    }

    @Test
    void startWaitsForModuleToBecomeHealthy() {
        when(lifecycle.start(registry.active())).thenReturn(true);
        when(lifecycle.isRunning(TestModules.BASIC)).thenReturn(true);
        var result = sut.handle("start " + TestModules.BASIC, token -> {});
        assertThat(result).contains("ready");
    }

    private String testFile() throws IOException {
        Path pdf = Files.createTempFile("doc", ".pdf");
        Files.write(pdf, new byte[]{0x25, 0x50, 0x44, 0x46});
        return pdf.toString();
    }
}