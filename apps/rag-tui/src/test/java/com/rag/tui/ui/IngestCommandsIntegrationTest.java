package com.rag.tui.ui;

import com.rag.common.core.services.FileDocumentLoader;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link IngestCommands} against a real HTTP stub module: real REST
 * ingest + health endpoints, real {@link FileDocumentLoader} on temp files, no
 * mocks and no Spring context.
 */
class IngestCommandsIntegrationTest {

    private StubModuleServer stub;
    private IngestCommands sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        var apiClient = new RagApiClient(registry, RestClient.builder());
        var healthClient = new ModuleHealthClient(RestClient.builder());
        sut = new IngestCommands(
                new CommandDispatcher.RagClients(apiClient, null, null, new FileDocumentLoader(),
                        healthClient, null),
                new NoopPrompter(new StringReader("")),
                new DuplicateFinder(registry, apiClient, healthClient), null);
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void addFileSubmitsBinaryFileOverHttpAsMultipart() throws IOException {
        Path pdf = Files.createTempFile("doc", ".pdf");
        byte[] bytes = "%PDF-1.4 fake binary content \u0000\u0001\u0002".getBytes(StandardCharsets.UTF_8);
        Files.write(pdf, bytes);
        List<String> tokens = new ArrayList<>();
        var result = sut.addFile(pdf.toString(), tokens::add);
        assertThat(result).contains("submitted", "i-1");
        await(tokens, "2 chunks");
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("2 chunks"));
        assertThat(stub.lastIngestContentType()).startsWith("multipart/form-data");
        var body = new String(stub.lastIngestBody(), StandardCharsets.UTF_8);
        assertThat(body).contains(pdf.getFileName().toString());
    }

    @Test
    void addFileSkipsDuplicateByContentHashOverHttp() throws Exception {
        Path txt = Files.createTempFile("dup", ".txt");
        byte[] bytes = "identical rag content".getBytes(StandardCharsets.UTF_8);
        Files.write(txt, bytes);
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        stub.documents("[{\"documentId\":\"existing-1\",\"title\":\"old.txt\",\"chunkCount\":3,"
                + "\"metadata\":{\"contentHash\":\"" + hash + "\"}}]");
        var result = sut.addFile(txt.toString(), token -> {});
        assertThat(result).contains("already ingested", "existing-1");
        assertThat(stub.lastIngestBody()).isNull();
    }

    @Test
    void addFolderSubmitsNewFilesAndSkipsDuplicate() throws Exception {
        Path folder = Files.createTempDirectory("notes");
        var duplicate = "duplicate me".getBytes(StandardCharsets.UTF_8);
        Files.write(Files.createTempFile(folder, "a", ".txt"), duplicate);
        Files.write(Files.createTempFile(folder, "b", ".txt"), "brand new".getBytes(StandardCharsets.UTF_8));
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(duplicate));
        stub.documents("[{\"documentId\":\"existing-1\",\"title\":\"a.txt\",\"chunkCount\":1,"
                + "\"metadata\":{\"contentHash\":\"" + hash + "\"}}]");
        var result = sut.addFolder(folder.toString(), token -> {});
        assertThat(result).contains("Submitted 1 of 2 files", "1 duplicate(s) skipped");
    }

    @Test
    void addUrlIngestsWhenNoDuplicateFound() {
        var result = sut.addUrl("https://example.com/guide");
        assertThat(result).contains("i-1", "2 chunks");
        assertThat(stub.lastIngestUrl()).contains("https://example.com/guide");
    }

    @Test
    void addUrlSkipsDuplicateByUriOverHttp() {
        stub.documents("[{\"documentId\":\"web-1\",\"title\":\"Old page\",\"chunkCount\":5,"
                + "\"metadata\":{\"source\":\"https://example.com/guide\"}}]");
        var result = sut.addUrl("https://example.com/guide");
        assertThat(result).contains("already ingested", "web-1");
        assertThat(stub.lastIngestUrl()).isNull();
    }

    private static void await(List<String> tokens, String needle) {
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }
}