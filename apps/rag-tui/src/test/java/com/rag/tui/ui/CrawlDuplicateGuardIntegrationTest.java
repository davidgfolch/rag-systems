package com.rag.tui.ui;

import com.rag.common.core.util.WebUrls;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link CrawlDuplicateGuard} over a real HTTP stub: overriding a
 * duplicate must delete the existing document before the page is re-ingested,
 * while skipping must leave it untouched.
 */
class CrawlDuplicateGuardIntegrationTest {

    private static final String URL = "https://example.com/page";
    private static final String DOCUMENT_ID = "web-1";

    private StubModuleServer stub;
    private RagApiClient apiClient;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(
                List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        apiClient = new RagApiClient(registry, RestClient.builder(), RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void deletesTheExistingDocumentWhenOverridingOverHttp() {
        var sut = new CrawlDuplicateGuard(index(), noopPrompter(), apiClient, true, false);
        assertThat(sut.overrideFor(URL)).isTrue();
        assertThat(stub.lastDeletedDocumentId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void neverDeletesTheExistingDocumentWhenSkippingOverHttp() {
        var sut = new CrawlDuplicateGuard(index(), noopPrompter(), apiClient, false, true);
        assertThat(sut.overrideFor(URL)).isFalse();
        assertThat(stub.lastDeletedDocumentId()).isNull();
    }

    private Map<String, DuplicateFinder.Match> index() {
        return Map.of(WebUrls.key(URL),
                new DuplicateFinder.Match(DOCUMENT_ID, TestModules.BASIC, stub.baseUrl()));
    }

    private static Prompter noopPrompter() {
        return new NoopPrompter(new StringReader(""));
    }
}
