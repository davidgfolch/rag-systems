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
 * Exercises {@link AsyncIngestPoller} against a real HTTP stub module: the
 * poller repeatedly reads ingest status over a real socket, reports completion
 * tokens, and clears the in-flight content hash once the job is done.
 */
class AsyncIngestPollerIntegrationTest {

    private StubModuleServer stub;
    private AsyncIngestPoller sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        var apiClient = new RagApiClient(registry, RestClient.builder());
        var healthClient = new ModuleHealthClient(RestClient.builder());
        sut = new AsyncIngestPoller(
                new CommandDispatcher.RagClients(apiClient, null, null, null, healthClient, null),
                null, 30L);
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void reportsCompletionWhenIngestFinishes() {
        List<String> tokens = new ArrayList<>();
        sut.start("i-1", "hash-a", tokens::add);
        await(tokens, "complete");
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("2 chunks"));
    }

    @Test
    void clearsInFlightHashAfterJobCompletes() {
        assertThat(sut.isInFlight("hash-b")).isFalse();
        assertThat(sut.markInFlight("hash-b")).isTrue();
        assertThat(sut.isInFlight("hash-b")).isTrue();
        sut.start("i-1", "hash-b", token -> {});
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !sut.isInFlight("hash-b"));
    }

    @Test
    void rejectsDuplicateMarkingWhileInFlight() {
        assertThat(sut.markInFlight("hash-c")).isTrue();
        assertThat(sut.markInFlight("hash-c")).isFalse();
        assertThat(sut.isInFlight("hash-c")).isTrue();
    }

    private static void await(List<String> tokens, String needle) {
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }
}