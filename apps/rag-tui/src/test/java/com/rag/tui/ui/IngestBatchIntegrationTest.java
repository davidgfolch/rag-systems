package com.rag.tui.ui;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link IngestBatch} end to end over a real HTTP stub module: the
 * poller drives each job to a terminal state and the batch prints a single
 * summary only after every submitted job is finished.
 */
class IngestBatchIntegrationTest {

    private StubModuleServer stub;
    private AsyncIngestPoller poller;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        var apiClient = new RagApiClient(registry, RestClient.builder());
        var healthClient = new ModuleHealthClient(RestClient.builder());
        poller = new AsyncIngestPoller(
                new CommandDispatcher.RagClients(apiClient, null, null, null, healthClient, null),
                null, 30L);
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void reportsSummaryWhenEveryJobFinishes() {
        List<String> tokens = new ArrayList<>();
        var batch = new IngestBatch("Crawl ingestion", tokens::add);
        poller.start("i-1", "hash-d", "page https://x.com/a", tokens::add, batch);
        batch.close();
        await(tokens, "Crawl ingestion");
        assertThat(tokens).anyMatch(t -> t.contains("1 page(s) complete")
                && t.contains("0 failed") && t.contains("2 chunk(s) total"));
    }

    @Test
    void reportsFailedJobsInSummary() {
        stub.ingestStatus("{\"documentId\":\"i-1\",\"state\":\"FAILED\",\"message\":\"no text\"}");
        List<String> tokens = new ArrayList<>();
        var batch = new IngestBatch("Crawl ingestion", tokens::add);
        poller.start("i-1", "hash-e", "page https://x.com/b", tokens::add, batch);
        batch.close();
        await(tokens, "Crawl ingestion");
        assertThat(tokens).anyMatch(t -> t.contains("1 failed")
                && t.contains("page https://x.com/b: no text"));
    }

    private static void await(List<String> tokens, String needle) {
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }
}
