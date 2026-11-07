package com.rag.tui.ui;

import com.rag.common.core.util.WebUrls;
import com.rag.tui.client.RagApiClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlDuplicateGuardTest {

    private static final String URL = "https://example.com/page";
    private static final String OTHER = "https://example.com/other";
    private static final String BASE = "http://localhost:8081";

    private final Prompter prompter = mock(Prompter.class);
    private final RagApiClient apiClient = mock(RagApiClient.class);

    @Test
    void doesNotOverrideWhenThereIsNoDuplicate() {
        var sut = new CrawlDuplicateGuard(Map.of(), prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isFalse();
        verify(prompter, never()).overrideDecision(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void doesNotOverrideWhenUrlCannotBeKeyed() {
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, false, false);
        assertThat(sut.overrideFor("not-a-url")).isFalse();
    }

    @Test
    void skipsAllDuplicatesWithoutPrompting() {
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, false, true);
        assertThat(sut.overrideFor(URL)).isFalse();
        verify(prompter, never()).overrideDecision(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void overridesAllDuplicatesWithoutPrompting() {
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, true, false);
        assertThat(sut.overrideFor(URL)).isTrue();
        verify(prompter, never()).overrideDecision(anyString());
        verify(apiClient).deleteDocument(BASE, "d1");
    }

    @Test
    void overridesSingleDuplicateOnConfirmation() {
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.OVERRIDE);
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isTrue();
        verify(apiClient).deleteDocument(BASE, "d1");
    }

    @Test
    void ignoresSingleDuplicateWhenDeclined() {
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.SKIP);
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isFalse();
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void nullDecisionIsTreatedAsIgnore() {
        when(prompter.overrideDecision(anyString())).thenReturn(null);
        var sut = new CrawlDuplicateGuard(Map.of(WebUrls.key(URL), match("d1")), prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isFalse();
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void overrideAllDecisionStopsPromptingForLaterDuplicates() {
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.OVERRIDE_ALL);
        var sut = new CrawlDuplicateGuard(
                Map.of(WebUrls.key(URL), match("d1"), WebUrls.key(OTHER), match("d2")),
                prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isTrue();
        assertThat(sut.overrideFor(OTHER)).isTrue();
        verify(prompter, times(1)).overrideDecision(anyString());
        verify(apiClient).deleteDocument(BASE, "d1");
        verify(apiClient).deleteDocument(BASE, "d2");
    }

    @Test
    void skipAllDecisionStopsPromptingForLaterDuplicates() {
        when(prompter.overrideDecision(anyString())).thenReturn(Prompter.OverrideDecision.SKIP_ALL);
        var sut = new CrawlDuplicateGuard(
                Map.of(WebUrls.key(URL), match("d1"), WebUrls.key(OTHER), match("d2")),
                prompter, apiClient, false, false);
        assertThat(sut.overrideFor(URL)).isFalse();
        assertThat(sut.overrideFor(OTHER)).isFalse();
        verify(prompter, times(1)).overrideDecision(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    private static DuplicateFinder.Match match(String documentId) {
        return new DuplicateFinder.Match(documentId, "rag-basic", BASE);
    }
}
