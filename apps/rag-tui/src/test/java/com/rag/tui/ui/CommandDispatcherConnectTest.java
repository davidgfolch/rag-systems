package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherConnectTest extends AbstractCommandDispatcherTest {

    @Test
    void connectShowsProviderStatusAndCatalogFreshness() {
        mockStatus();
        when(providerClient.catalog()).thenReturn(catalog(Instant.parse("2026-09-08T10:00:00Z")));
        var result = handle("connect");
        assertThat(result)
                .contains("chat: ollama/phi4", "embedding: ollama/nomic-embed-text", "dimension 768")
                .contains("fetched 2026-09-08T10:00:00Z", "2 models");
    }

    @Test
    void connectReportsCatalogNotFetchedYet() {
        mockStatus();
        when(providerClient.catalog()).thenReturn(catalog(null));
        var result = handle("connect");
        assertThat(result).contains("not fetched yet");
    }

    @Test
    void connectListsCatalogGroupedByProvider() {
        when(providerClient.catalog()).thenReturn(catalog(Instant.parse("2026-09-08T10:00:00Z")));
        var result = handle("connect catalog");
        assertThat(result)
                .contains("ollama:", "openai:")
                .contains("phi4 (Phi-4)")
                .contains("gpt-4o (GPT-4o)", "ctx 128000");
    }

    @Test
    void connectFiltersCatalogByProvider() {
        when(providerClient.catalog()).thenReturn(catalog(Instant.parse("2026-09-08T10:00:00Z")));
        var result = handle("connect catalog open");
        assertThat(result).contains("openai:").doesNotContain("ollama:");
    }

    @Test
    void connectReportsUnknownProviderFilter() {
        when(providerClient.catalog()).thenReturn(catalog(Instant.parse("2026-09-08T10:00:00Z")));
        var result = handle("connect catalog meta");
        assertThat(result).contains("No models for provider 'meta'");
    }

    @Test
    void connectReportsEmptyCatalog() {
        when(providerClient.catalog()).thenReturn(new ModelCatalogDTO(List.of(), "https://models.dev/api.json", null));
        var result = handle("connect catalog");
        assertThat(result).contains("Catalog is empty", "connect refresh");
    }

    @Test
    void connectSwitchesChatModel() {
        var result = handle("connect chat ollama phi4");
        assertThat(result).contains("Chat model switched", "ollama/phi4");
        verify(providerClient).switchChat("ollama", "phi4");
    }

    @Test
    void connectSwitchesEmbeddingModel() {
        var result = handle("connect embedding openai text-embedding-3-small");
        assertThat(result).contains("Embedding model switched", "openai/text-embedding-3-small");
        verify(providerClient).switchEmbedding("openai", "text-embedding-3-small");
    }

    @Test
    void connectCancelsSwitchWhenModelPromptCancelled() {
        when(providerClient.catalog()).thenReturn(catalog(Instant.parse("2026-09-08T10:00:00Z")));
        var result = handle("connect chat ollama");
        assertThat(result).isEmpty();
        verify(providerClient, never()).switchChat(anyString(), anyString());
    }

    @Test
    void connectRefreshesCatalog() {
        when(providerClient.refreshCatalog()).thenReturn(catalog(Instant.parse("2026-09-08T11:00:00Z")));
        var result = handle("connect refresh");
        assertThat(result).contains("Catalog refreshed", "2 models");
        verify(providerClient).refreshCatalog();
    }

    @Test
    void connectShowsUsageForUnknownSubcommand() {
        var result = handle("connect frobnicate");
        assertThat(result).contains("Usage: connect");
    }

    @Test
    void connectReportsUnreachableProvider() {
        when(providerClient.status()).thenThrow(new RestClientException("Connection refused"));
        var result = handle("connect");
        assertThat(result).contains("Module unreachable", "Connection refused");
    }

    @Test
    void providerSummaryReturnsActiveSpecsOrEmptyWhenUnreachable() {
        mockStatus();
        assertThat(sut.providerSummary()).contains("ollama/phi4");
        when(providerClient.status()).thenThrow(new RestClientException("Connection refused"));
        assertThat(sut.providerSummary()).isEmpty();
    }
}