package com.rag.tui.ui;

import com.rag.contract.model.DocumentSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static com.rag.tui.testfixture.TestCrawlFixtures.SESSION;
import static com.rag.tui.testfixture.TestCrawlFixtures.URL;
import static com.rag.tui.testfixture.TestCrawlFixtures.page;
import static com.rag.tui.testfixture.TestCrawlFixtures.skippedStep;
import static com.rag.tui.testfixture.TestCrawlFixtures.start;
import static com.rag.tui.testfixture.TestCrawlFixtures.step;
import static com.rag.tui.testfixture.TestDocumentSummaries.withSource;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddUrlDuplicateTest extends AbstractCommandDispatcherTest {

    private static final String NEXT = "https://x.com/a";

    @BeforeEach
    void stubPrompts() {
        when(prompter.prompt(anyString())).thenReturn("");
        when(prompter.confirm(anyString())).thenReturn(true);
    }

    @Test
    void addUrlAllSkipsExistingPageWithoutPrompting() {
        givenExisting(URL);
        when(apiClient.startCrawl(URL, Integer.MAX_VALUE, true)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(skippedStep(0, null));
        var result = handle("add-url " + URL + " --all");
        assertThat(result).contains("Crawl finished", "1 page(s) skipped");
        verify(prompter, never()).overrideDecision(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void addUrlOverrideAllDeletesAndReingests() {
        givenExisting(URL);
        when(apiClient.startCrawl(URL, Integer.MAX_VALUE, true)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", true)).thenReturn(step("d2", 0, null));
        var result = handle("add-url " + URL + " --all --override-all");
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        verify(prompter, never()).overrideDecision(anyString());
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).stepCrawl(SESSION, "", true);
    }

    @Test
    void addUrlOverridesDuplicateWhenUserConfirms() {
        givenExisting(URL);
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.OVERRIDE);
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", true)).thenReturn(step("d2", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "1 page(s) submitted");
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).stepCrawl(SESSION, "", true);
    }

    @Test
    void addUrlSkipsDuplicateWhenUserIgnores() {
        givenExisting(URL);
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.SKIP);
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false)).thenReturn(skippedStep(0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "1 page(s) skipped");
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void addUrlOverrideRestAppliesToLaterDuplicates() {
        givenExisting(URL, NEXT);
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.OVERRIDE_ALL);
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", true))
                .thenReturn(step("d3", 0, page(NEXT)))
                .thenReturn(step("d4", 0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "2 page(s) submitted");
        verify(prompter, times(1)).overrideDecision(anyString());
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).deleteDocument(BASIC_URL, "d2");
    }

    @Test
    void addUrlSkipRestAppliesToLaterDuplicates() {
        givenExisting(URL, NEXT);
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.SKIP_ALL);
        when(apiClient.startCrawl(URL)).thenReturn(start(1));
        when(apiClient.stepCrawl(SESSION, "", false))
                .thenReturn(skippedStep(0, page(NEXT)))
                .thenReturn(skippedStep(0, null));
        var result = handle("add-url " + URL);
        assertThat(result).contains("Crawl finished", "2 page(s) skipped");
        verify(prompter, times(1)).overrideDecision(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    private void givenExisting(String... urls) {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        var documents = new ArrayList<DocumentSummaryDTO>();
        for (int i = 0; i < urls.length; i++) {
            documents.add(withSource("d" + (i + 1), "old", urls[i]));
        }
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(documents);
    }
}
