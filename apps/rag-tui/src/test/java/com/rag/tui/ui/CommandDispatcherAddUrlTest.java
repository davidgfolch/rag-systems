package com.rag.tui.ui;

import com.rag.contract.model.CrawlPageDTO;
import com.rag.contract.model.CrawlStartResponse;
import com.rag.contract.model.CrawlStepResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.tui.testfixture.TestDocumentSummaries.withSource;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddUrlTest extends AbstractCommandDispatcherTest {

    private static final String URL = "https://x.com/page";
    private static final String SESSION = "s1";

    @Test
    void addUrlSkipsCrawlWhenUriAlreadyIngested() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", URL)));
        var result = handle("add-url " + URL);
        assertThat(result).contains("already ingested", "d1");
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void addUrlOverridesAndCrawlsWhenConfirmed() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", URL)));
        when(prompter.confirm(anyString())).thenReturn(true);
        when(prompter.prompt(anyString())).thenReturn("");
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "")).thenReturn(step("d2", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).startCrawl(URL);
    }

    @Test
    void addUrlSkipsWhenOverrideDeclined() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", URL)));
        when(prompter.confirm(anyString())).thenReturn(false);
        var result = handle("add-url " + URL);
        assertThat(result).contains("Skipped");
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void addUrlCrawlsWhenNoUriDuplicateFound() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "")).thenReturn(step("d2", 0, null));
        when(prompter.prompt(anyString())).thenReturn("");
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        verify(prompter, never()).confirm(anyString());
    }

    @Test
    void addUrlAsksGoOnOnceAfterInitialSetThenContinues() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "")).thenReturn(step("d1", 1, page("https://x.com/a")))
                .thenReturn(step("d2", 0, page("https://x.com/b")))
                .thenReturn(step("d3", 0, null));
        when(prompter.confirm(anyString())).thenReturn(true);
        when(prompter.prompt(anyString())).thenReturn("");
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "3 page(s) submitted");
        verify(prompter, times(1)).confirm(anyString());
    }

    @Test
    void addUrlStopsWhenGoOnDeclined() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "")).thenReturn(step("d1", 0, page("https://x.com/a")));
        when(prompter.confirm(anyString())).thenReturn(false);
        when(prompter.prompt(anyString())).thenReturn("");
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl cancelled", "1 page(s) submitted");
        verify(apiClient).cancelCrawl(SESSION);
    }

    @Test
    void addUrlCancelsWhenCancelCommandEnteredAtPrompt() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(prompter.prompt(anyString())).thenReturn(CrawlCommands.CANCEL_COMMAND);
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl cancelled");
        verify(apiClient, never()).stepCrawl(anyString(), anyString());
        verify(apiClient).cancelCrawl(SESSION);
    }

    @Test
    void addUrlPreservesUrlCaseFromInput() {
        when(apiClient.startCrawl("https://x.com/Page")).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "")).thenReturn(step("d1", 0, null));
        when(prompter.prompt(anyString())).thenReturn("");
        var result = handle("ADD-URL https://x.com/Page");
        assertThat(result).contains("Crawl finished");
        verify(apiClient).startCrawl("https://x.com/Page");
    }

    @Test
    void addUrlPassesQuestionVerbatim() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "What Is RAG?")).thenReturn(step("d1", 0, null));
        when(prompter.prompt(anyString())).thenReturn("What Is RAG?");
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished");
        verify(apiClient).stepCrawl(SESSION, "What Is RAG?");
    }

    @Test
    void addUrlCancelledPromptShowsUsage() {
        when(prompter.prompt("URL: ")).thenReturn(null);
        var result = handle("add-url");
        assertThat(result).isEqualTo("Usage: add-url <url>");
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void cancelActiveReportsWhenNoCrawlIsRunning() {
        assertThat(handle("add-url-cancel")).isEqualTo("No URL crawl is active.");
    }

    private static CrawlStartResponse start(int initialRemaining) {
        return new CrawlStartResponse().sessionId(SESSION).page(page(URL))
                .initialRemaining(initialRemaining).hasMore(true);
    }

    private static CrawlStepResponse step(String documentId, int initialRemaining, CrawlPageDTO next) {
        return new CrawlStepResponse().sessionId(SESSION).documentId(documentId)
                .pageUrl(URL).title("Page").ingested(documentId != null)
                .initialRemaining(initialRemaining).hasMore(next != null)
                .skippedLinks(0).nextPage(next);
    }

    private static CrawlPageDTO page(String url) {
        return new CrawlPageDTO(url, "Page");
    }
}
