package com.rag.tui.ui;

import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.tui.testfixture.TestDocumentSummaries.withHash;
import static com.rag.tui.testfixture.TestDocumentSummaries.withId;
import static com.rag.tui.testfixture.TestDocumentSummaries.withSource;
import static com.rag.tui.testfixture.TestModules.ADVANCED;
import static com.rag.tui.testfixture.TestModules.ADVANCED_URL;
import static com.rag.tui.testfixture.TestModules.BASIC;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static com.rag.tui.testfixture.TestModules.advanced;
import static com.rag.tui.testfixture.TestModules.basic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuplicateFinderTest {

    private final RagApiClient apiClient = mock(RagApiClient.class);
    private final ModuleHealthClient healthClient = mock(ModuleHealthClient.class);
    private final ModuleRegistry registry = new ModuleRegistry(List.of(basic(), advanced()), BASIC);
    private final DuplicateFinder sut = new DuplicateFinder(registry, apiClient, healthClient);

    @Test
    void shouldReturnEmptyWhenNoModuleHasMatchingHash() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "a.txt", "abc")));
        assertThat(sut.findByContentHash("zzz")).isEmpty();
    }

    @Test
    void shouldReturnDocIdAndModuleWhenHashMatches() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "a.txt", "abc")));
        var match = sut.findByContentHash("abc").orElseThrow();
        assertThat(match.documentId()).isEqualTo("d1");
        assertThat(match.moduleName()).isEqualTo(BASIC);
        assertThat(match.baseUrl()).isEqualTo(BASIC_URL);
    }

    @Test
    void shouldReturnFirstMatchAcrossModulesWhenHashMatchesOnLaterModule() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "a.txt", "aaa")));
        when(healthClient.isUp(ADVANCED_URL)).thenReturn(true);
        when(apiClient.listDocuments(ADVANCED_URL)).thenReturn(List.of(withHash("d2", "b.txt", "abc")));
        var match = sut.findByContentHash("abc").orElseThrow();
        assertThat(match.documentId()).isEqualTo("d2");
        assertThat(match.moduleName()).isEqualTo(ADVANCED);
    }

    @Test
    void shouldReturnEmptyWhenNoModuleReachable() {
        when(healthClient.isUp(anyString())).thenReturn(false);
        assertThat(sut.findByContentHash("abc")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoModuleHasMatchingSource() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        assertThat(sut.findBySource("https://y.com/other")).isEmpty();
    }

    @Test
    void shouldReturnMatchWhenUriEqualsIgnoringFragmentAndTrailingSlash() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page/")));
        assertThat(sut.findBySource("https://x.com/page#top")).isPresent();
    }

    @Test
    void shouldReturnMatchWhenUriDiffersOnlyByScheme() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "http://x.com/page")));
        assertThat(sut.findBySource("https://x.com/page")).isPresent();
    }

    @Test
    void shouldNotMatchWhenPathDiffers() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        assertThat(sut.findBySource("https://x.com/other")).isEmpty();
    }

    @Test
    void shouldNotMatchWhenHostDiffers() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "https://x.com/page")));
        assertThat(sut.findBySource("https://y.com/page")).isEmpty();
    }

    @Test
    void shouldNotMatchWhenSourceMetadataMissing() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        assertThat(sut.findBySource("https://x.com/page")).isEmpty();
    }

    @Test
    void shouldNotTreatFileDocumentAsUrlDuplicateWhenSourceIsLocalPath() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "note", "docs/report.pdf")));
        assertThat(sut.findBySource("https://x.com/docs/report.pdf")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnparseableStoredSource() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withSource("d1", "old", "::::not a uri:::")));
        assertThat(sut.findBySource("https://x.com/page")).isEmpty();
    }
}
