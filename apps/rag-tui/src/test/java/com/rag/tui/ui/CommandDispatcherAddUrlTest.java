package com.rag.tui.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static com.rag.tui.testfixture.TestCrawlFixtures.SESSION;
import static com.rag.tui.testfixture.TestCrawlFixtures.URL;
import static com.rag.tui.testfixture.TestCrawlFixtures.page;
import static com.rag.tui.testfixture.TestCrawlFixtures.start;
import static com.rag.tui.testfixture.TestCrawlFixtures.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddUrlTest extends AbstractCommandDispatcherTest {

    private static final String USAGE =
            "Usage: add-url <url> [--max-pages N] [--all] [--override-all] [--no-discover]";

    @BeforeEach
    void stubPrompts() {
        when(prompter.prompt(anyString())).thenReturn("");
        when(prompter.confirm(anyString())).thenReturn(true);
    }

    @Test
    void addUrlAsksRankingQuestionOnceForWholeCrawl() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(step("d1", 1, page("https://x.com/a")))
                .thenReturn(step("d2", 0, page("https://x.com/b")))
                .thenReturn(step("d3", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "3 page(s) submitted");
        verify(prompter, times(1)).prompt(anyString());
        verify(prompter, times(1)).confirm(anyString());
    }

    @Test
    void addUrlAllFlagCrawlsWithoutAnyPrompt() {
        when(apiClient.startCrawl(URL, Integer.MAX_VALUE, true)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(step("d1", 0, page("https://x.com/a")))
                .thenReturn(step("d2", 0, null));
        var result = handle("add-url " + URL + " --all");
        assertThat(result).contains("Crawl finished", "2 page(s) submitted");
        verify(prompter, never()).prompt(anyString());
        verify(prompter, never()).confirm(anyString());
        verify(prompter, never()).overrideDecision(anyString());
    }

    @Test
    void addUrlMaxPagesSuppressesGoOn() {
        when(apiClient.startCrawl(URL, 7, true)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(step("d1", 0, page("https://x.com/a")))
                .thenReturn(step("d2", 0, null));
        var result = handle("add-url " + URL + " --max-pages 7");
        assertThat(result).contains("Crawl finished", "2 page(s) submitted");
        verify(prompter, never()).confirm(anyString());
        verify(prompter, times(1)).prompt(anyString());
    }

    @Test
    void addUrlAsksGoOnOnceAfterInitialSetThenContinues() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(step("d1", 1, page("https://x.com/a")))
                .thenReturn(step("d2", 0, page("https://x.com/b")))
                .thenReturn(step("d3", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "3 page(s) submitted");
        verify(prompter, times(1)).confirm(anyString());
    }

    @Test
    void addUrlStopsWhenGoOnDeclined() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(step("d1", 0, page("https://x.com/a")));
        when(prompter.confirm(anyString())).thenReturn(false);
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl cancelled", "1 page(s) submitted");
        verify(apiClient).cancelCrawl(SESSION);
    }

    @Test
    void addUrlCancelsWhenCancelCommandEnteredAtRankingPrompt() {
        when(prompter.prompt(anyString())).thenReturn(CrawlCommands.CANCEL_COMMAND);
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl cancelled");
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void addUrlReportsPartialSummaryWhenStepRequestFails() {
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(step("d1", 1, page("https://x.com/a")))
                .thenThrow(new ResourceAccessException("Request cancelled"));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl interrupted", "Request cancelled", "Crawl partial",
                "1 page(s) submitted");
        verify(apiClient).cancelCrawl(SESSION);
    }

    @Test
    void addUrlPreservesUrlCaseFromInput() {
        when(apiClient.startCrawl("https://x.com/Page")).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(step("d1", 0, null));
        var result = handle("ADD-URL https://x.com/Page");
        assertThat(result).contains("Crawl finished");
        verify(apiClient).startCrawl("https://x.com/Page");
    }

    @Test
    void addUrlPassesQuestionVerbatim() {
        when(prompter.prompt(anyString())).thenReturn("What Is RAG?");
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "What Is RAG?", false)).thenReturn(step("d1", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished");
        verify(apiClient).stepCrawl(SESSION, "What Is RAG?", false);
    }

    @Test
    void addUrlPassesMaxPagesAndDiscoveryFlags() {
        when(apiClient.startCrawl(URL, 7, false)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(step("d1", 0, null));
        var result = handle("add-url " + URL + " --max-pages 7 --no-discover");
        assertThat(result).contains("Crawl finished");
        verify(apiClient).startCrawl(URL, 7, false);
    }

    @Test
    void addUrlAllFlagRemovesThePageCap() {
        when(apiClient.startCrawl(URL, Integer.MAX_VALUE, true)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(step("d1", 0, null));
        var result = handle("add-url " + URL + " --all");
        assertThat(result).contains("Crawl finished");
        verify(apiClient).startCrawl(URL, Integer.MAX_VALUE, true);
    }

    @Test
    void addUrlRejectsInvalidMaxPages() {
        var result = handle("add-url " + URL + " --max-pages nope");
        assertThat(result).isEqualTo(USAGE);
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void addUrlCancelledPromptShowsUsage() {
        when(prompter.prompt("URL: ")).thenReturn(null);
        var result = handle("add-url");
        assertThat(result).isEqualTo(USAGE);
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void addUrlAllStillPromptsForMissingUrl() {
        when(prompter.prompt("URL: ")).thenReturn("");
        var result = handle("add-url --all");
        assertThat(result).isEqualTo(USAGE);
        verify(apiClient, never()).startCrawl(anyString());
    }

    @Test
    void cancelActiveReportsWhenNoCrawlIsRunning() {
        assertThat(handle("add-url-cancel")).isEqualTo("No URL crawl is active.");
    }
}
