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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link CrawlCommands} against a real HTTP stub module: the module
 * drives the frontier, the TUI only prompts and submits one page per step, and
 * each submitted page is polled asynchronously.
 */
class CrawlCommandsIntegrationTest {

    private static final String URL = "https://example.com/root";
    private static final String SESSION = "c-1";

    private StubModuleServer stub;
    private ScriptedPrompter prompter;
    private CrawlCommands sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        prompter = new ScriptedPrompter();
        var registry = new ModuleRegistry(List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        var apiClient = new RagApiClient(registry, RestClient.builder(), RestClient.builder());
        var healthClient = new ModuleHealthClient(RestClient.builder());
        var clients = new CommandDispatcher.RagClients(apiClient, null, null, null, healthClient, null);
        sut = new CrawlCommands(clients, prompter,
                new DuplicateFinder(registry, apiClient, healthClient),
                new AsyncIngestPoller(clients, null, 20L));
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void crawlsRootAndSubmitsAsyncIngestionOverHttp() {
        List<String> tokens = new ArrayList<>();
        var result = sut.addUrl(URL, tokens::add);
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        assertThat(stub.lastCrawlUrl()).contains(URL);
        assertThat(stub.lastCrawlStepBody()).contains("question").contains("\"override\":false");
        assertThat(stub.lastCrawlCancelledSession()).isEqualTo(SESSION);
        await(tokens, "page " + URL + " complete");
        await(tokens, "Crawl ingestion");
        assertThat(tokens).anyMatch(t -> t.contains("1 page(s) complete")
                && t.contains("0 failed") && t.contains("2 chunk(s) total"));
    }

    @Test
    void passesCrawlOptionsWhenFlagsAreGiven() {
        var result = sut.addUrl(URL + " --max-pages 7 --no-discover", token -> {});
        assertThat(result).contains("Crawl finished");
        assertThat(stub.lastCrawlUrl())
                .contains("\"maxPages\":7")
                .contains("\"discover\":false");
    }

    @Test
    void allFlagCrawlsWithoutAnyPrompt() {
        stub.crawlStart(startJson(1));
        stub.crawlSteps(
                stepJson("i-1", 1, "https://example.com/a"),
                stepJson("i-2", 0, null));
        var result = sut.addUrl(URL + " --all", token -> {});
        assertThat(result).contains("Crawl finished", "2 page(s) submitted");
        assertThat(prompter.promptCount()).isZero();
        assertThat(prompter.confirmCount()).isZero();
    }

    @Test
    void skipsExistingPageWhenOverrideIgnoredButKeepsCrawling() {
        stub.documents("[{\"documentId\":\"web-1\",\"title\":\"Old page\",\"chunkCount\":5,"
                + "\"metadata\":{\"source\":\"" + URL + "\"}}]");
        prompter.overrideAnswer(Prompter.OverrideDecision.SKIP);
        stub.crawlStart(startJson(0));
        stub.crawlSteps(skippedStepJson(0));
        var result = sut.addUrl(URL, token -> {});
        assertThat(result).contains("Crawl finished", "1 page(s) skipped");
        assertThat(stub.lastCrawlUrl()).contains(URL);
        assertThat(stub.lastCrawlStepBody()).contains("\"override\":false");
        assertThat(stub.lastDeletedDocumentId()).isNull();
    }

    @Test
    void overrideAllDeletesAndReingestsOverHttp() {
        stub.documents("[{\"documentId\":\"web-1\",\"title\":\"Old page\",\"chunkCount\":5,"
                + "\"metadata\":{\"source\":\"" + URL + "\"}}]");
        stub.crawlStart(startJson(0));
        stub.crawlSteps(stepJson("i-2", 0, null));
        var result = sut.addUrl(URL + " --all --override-all", token -> {});
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        assertThat(stub.lastDeletedDocumentId()).isEqualTo("web-1");
        assertThat(stub.lastCrawlStepBody()).contains("\"override\":true");
    }

    @Test
    void asksGoOnOnceAfterInitialSetThenContinues() {
        stub.crawlStart(startJson(1));
        stub.crawlSteps(
                stepJson("i-1", 1, "https://example.com/a"),
                stepJson("i-2", 0, "https://example.com/b"),
                stepJson("i-3", 0, null));
        var result = sut.addUrl(URL, token -> {});
        assertThat(result).contains("Crawl finished", "3 page(s) submitted");
        assertThat(prompter.confirmCount()).isEqualTo(1);
    }

    @Test
    void cancelsCrawlWhenCancelCommandEnteredAtPrompt() {
        prompter.questionAnswer(CrawlCommands.CANCEL_COMMAND);
        var result = sut.addUrl(URL, token -> {});
        assertThat(result).contains("Crawl cancelled");
        assertThat(stub.lastCrawlUrl()).isNull();
        assertThat(stub.lastCrawlStepBody()).isNull();
    }

    @Test
    void cancelActiveReportsWhenNoCrawlIsRunning() {
        assertThat(sut.cancelActive()).isEqualTo("No URL crawl is active.");
    }

    private static String startJson(int initialRemaining) {
        return "{\"sessionId\":\"" + SESSION + "\",\"page\":{\"url\":\"" + URL + "\",\"title\":\"Root\"},"
                + "\"initialRemaining\":" + initialRemaining + ",\"hasMore\":true}";
    }

    private static String stepJson(String documentId, int initialRemaining, String nextUrl) {
        var next = nextUrl == null ? ""
                : ",\"nextPage\":{\"url\":\"" + nextUrl + "\",\"title\":\"Page\"}";
        return "{\"sessionId\":\"" + SESSION + "\",\"documentId\":\"" + documentId + "\","
                + "\"pageUrl\":\"" + URL + "\",\"title\":\"Root\",\"ingested\":true,"
                + "\"initialRemaining\":" + initialRemaining + ",\"hasMore\":" + (nextUrl != null)
                + ",\"skippedLinks\":0" + next + "}";
    }

    private static String skippedStepJson(int initialRemaining) {
        return "{\"sessionId\":\"" + SESSION + "\",\"pageUrl\":\"" + URL + "\",\"title\":\"Root\","
                + "\"ingested\":false,\"initialRemaining\":" + initialRemaining
                + ",\"hasMore\":false,\"skippedLinks\":0}";
    }

    private static void await(List<String> tokens, String needle) {
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    private static final class ScriptedPrompter implements Prompter {

        private final AtomicReference<String> questionAnswer = new AtomicReference<>("");
        private final AtomicReference<OverrideDecision> overrideAnswer =
                new AtomicReference<>(OverrideDecision.SKIP_ALL);
        private final AtomicBoolean confirmAnswer = new AtomicBoolean(true);
        private final AtomicInteger confirmCount = new AtomicInteger();
        private final AtomicInteger promptCount = new AtomicInteger();

        void questionAnswer(String answer) {
            questionAnswer.set(answer);
        }

        void overrideAnswer(OverrideDecision answer) {
            overrideAnswer.set(answer);
        }

        void confirmAnswer(boolean answer) {
            confirmAnswer.set(answer);
        }

        int confirmCount() {
            return confirmCount.get();
        }

        int promptCount() {
            return promptCount.get();
        }

        @Override
        public Optional<String> pick(String title, List<Choice> choices) {
            return Optional.empty();
        }

        @Override
        public String prompt(String promptText) {
            promptCount.incrementAndGet();
            return questionAnswer.get();
        }

        @Override
        public boolean confirm(String promptText) {
            confirmCount.incrementAndGet();
            return confirmAnswer.get();
        }

        @Override
        public OverrideDecision overrideDecision(String promptText) {
            return overrideAnswer.get();
        }
    }
}
