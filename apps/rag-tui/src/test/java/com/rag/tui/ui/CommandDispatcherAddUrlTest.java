package com.rag.tui.ui;

import com.rag.contract.model.IngestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.tui.testfixture.TestDocumentSummaries.withId;
import static com.rag.tui.testfixture.TestDocumentSummaries.withSource;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddUrlTest extends AbstractCommandDispatcherTest {

    @Test
    void addUrlSkipsIngestWhenUriAlreadyIngested() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        var result = handle("add-url https://x.com/page");
        assertThat(result).contains("already ingested", "d1");
        verify(apiClient, never()).ingestUrl(anyString());
    }

    @Test
    void addUrlOverridesAndReingestsWhenConfirmed() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        when(prompter.confirm(anyString())).thenReturn(true);
        when(apiClient.ingestUrl("https://x.com/page"))
                .thenReturn(new IngestResponse().documentId("d2").chunkCount(7));
        var result = handle("add-url https://x.com/page");
        assertThat(result).contains("d2", "7");
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).ingestUrl("https://x.com/page");
    }

    @Test
    void addUrlSkipsWhenOverrideDeclined() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        when(prompter.confirm(anyString())).thenReturn(false);
        var result = handle("add-url https://x.com/page");
        assertThat(result).contains("Skipped");
        verify(apiClient, never()).ingestUrl(anyString());
    }

    @Test
    void addUrlIngestsWhenNoUriDuplicateFound() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        when(apiClient.ingestUrl("https://x.com/page"))
                .thenReturn(new IngestResponse().documentId("d2").chunkCount(7));
        var result = handle("add-url https://x.com/page");
        assertThat(result).contains("d2");
        verify(prompter, never()).confirm(anyString());
    }

    @Test
    void ingestsUrlViaActiveModule() {
        when(apiClient.ingestUrl("https://example.com"))
                .thenReturn(new IngestResponse().documentId("d1").chunkCount(3));
        var result = handle("add-url https://example.com");
        assertThat(result).contains("d1", "3");
    }

    @Test
    void addUrlCancelledPromptShowsUsage() {
        when(prompter.prompt("URL: ")).thenReturn(null);
        var result = handle("add-url");
        assertThat(result).isEqualTo("Usage: add-url <url>");
        verify(apiClient, never()).ingestUrl(anyString());
    }
}