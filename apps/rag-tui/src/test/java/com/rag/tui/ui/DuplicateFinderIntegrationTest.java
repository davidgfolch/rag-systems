package com.rag.tui.ui;

import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DuplicateFinder} against a real HTTP stub module: real REST
 * list-documents + health endpoints, no mocks.
 */
class DuplicateFinderIntegrationTest {

    private StubModuleServer stub;
    private ModuleRegistry registry;
    private DuplicateFinder sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        registry = new ModuleRegistry(
                List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        sut = new DuplicateFinder(registry,
                new RagApiClient(registry, RestClient.builder()),
                new ModuleHealthClient(RestClient.builder()));
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void findsDocumentByContentHashOverHttp() throws Exception {
        Path txt = Files.createTempFile("dup", ".txt");
        byte[] bytes = "duplicate me".getBytes(StandardCharsets.UTF_8);
        Files.write(txt, bytes);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        stub.documents("[{\"documentId\":\"doc-9\",\"title\":\"old.txt\",\"chunkCount\":2,"
                + "\"metadata\":{\"contentHash\":\"" + hash + "\"}}]");
        var match = sut.findByContentHash(hash).orElseThrow();
        assertThat(match.documentId()).isEqualTo("doc-9");
        assertThat(match.moduleName()).isEqualTo(TestModules.BASIC);
        assertThat(match.baseUrl()).isEqualTo(stub.baseUrl());
    }

    @Test
    void returnsEmptyWhenNoDocumentCarriesTheHash() {
        stub.documents("[{\"documentId\":\"doc-9\",\"title\":\"old.txt\",\"chunkCount\":2,"
                + "\"metadata\":{\"contentHash\":\"other-hash\"}}]");
        assertThat(sut.findByContentHash("abc")).isEmpty();
    }

    @Test
    void findsDocumentBySourceUriOverHttp() {
        stub.documents("[{\"documentId\":\"web-9\",\"title\":\"Guide\",\"chunkCount\":4,"
                + "\"metadata\":{\"source\":\"https://example.com/guide/\"}}]");
        var match = sut.findBySource("https://example.com/guide#intro").orElseThrow();
        assertThat(match.documentId()).isEqualTo("web-9");
    }

    @Test
    void returnsEmptyWhenUriDiffersByPath() {
        stub.documents("[{\"documentId\":\"web-9\",\"title\":\"Guide\",\"chunkCount\":4,"
                + "\"metadata\":{\"source\":\"https://example.com/guide\"}}]");
        assertThat(sut.findBySource("https://example.com/other")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoModuleIsReachable() {
        var dead = new DuplicateFinder(
                new ModuleRegistry(List.of(TestModules.withUrl("http://localhost:1")), TestModules.BASIC),
                new RagApiClient(registry, RestClient.builder()),
                new ModuleHealthClient(RestClient.builder()));
        assertThat(dead.findByContentHash("abc")).isEmpty();
        assertThat(dead.findBySource("https://example.com/guide")).isEmpty();
    }
}
