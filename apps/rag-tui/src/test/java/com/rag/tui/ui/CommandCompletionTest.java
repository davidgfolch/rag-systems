package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandCompletionTest {

    private final ProviderClient providerClient = mock(ProviderClient.class);
    private final CommandCompletion sut = new CommandCompletion(
            new CommandRegistry(),
            new ModuleRegistry(List.of(
                    new Module("rag-basic", "http://localhost:8081"),
                    new Module("rag-advanced", "http://localhost:8082")), "rag-basic"),
            providerClient);

    private void catalog() {
        when(providerClient.catalog())
                .thenReturn(new ModelCatalogDTO(List.of(
                        new ProviderModelDTO("openai", "gpt-4o", null, null, null, null, null),
                        new ProviderModelDTO("openrouter", "qwen", null, null, null, null, null),
                        new ProviderModelDTO("ollama", "phi4", null, null, null, null, null),
                        new ProviderModelDTO("cohere", "command", null, null, null, null, null)), "models.dev", Instant.now()));
    }

    @Test
    void showsProvidersStartingWithTypedToken() {
        catalog();

        var result = sut.candidates("connect o", 9);

        assertThat(result).containsExactly("ollama", "openai", "openrouter");
    }

    @Test
    void showsCommandNamesWhileTypingCommand() {
        catalog();

        var result = sut.candidates("c", 1);

        assertThat(result).containsExactly("connect");
    }

    @Test
    void showsOnlySubcommandsForEmptyConnectArgument() {
        catalog();

        var result = sut.candidates("connect ", 8);

        assertThat(result).containsExactly("catalog", "chat", "embedding", "refresh");
    }

    @Test
    void showsMatchingProvidersOnTypedConnectToken() {
        catalog();

        var result = sut.candidates("connect o", 9);

        assertThat(result).containsExactly("ollama", "openai", "openrouter");
    }

    @Test
    void showsProvidersForChatSubcommandPosition() {
        catalog();

        var result = sut.candidates("connect chat o", 14);

        assertThat(result).containsExactly("ollama", "openai", "openrouter");
    }

    @Test
    void hidesProviderListUntilTokenIsTyped() {
        catalog();

        var result = sut.candidates("connect chat ", 13);

        assertThat(result).isEmpty();
    }

    @Test
    void hidesModelListUntilTokenIsTyped() {
        catalog();

        var result = sut.candidates("connect chat openai ", 20);

        assertThat(result).isEmpty();
    }

    @Test
    void showsModelsAfterChatProviderSelectedAndTyped() {
        catalog();

        var result = sut.candidates("connect chat openai g", 21);

        assertThat(result).contains("gpt-4o");
    }

    @Test
    void showsModuleNamesForUseCommand() {
        catalog();

        var result = sut.candidates("use ", 4);

        assertThat(result).containsExactly("rag-basic", "rag-advanced");
    }

    @Test
    void filtersModuleNamesByPrefix() {
        catalog();

        var result = sut.candidates("start rag-ad", 12);

        assertThat(result).containsExactly("rag-advanced");
    }

    @Test
    void noCandidatesForUnknownCommandArgument() {
        catalog();

        var result = sut.candidates("frobnicate x", 12);

        assertThat(result).isEmpty();
    }

    @Test
    void degradesGracefullyWhenCatalogUnreachable() {
        when(providerClient.catalog()).thenThrow(new RuntimeException("down"));

        var result = sut.candidates("connect o", 9);

        assertThat(result).containsExactly("catalog");
    }

    @Test
    void cachesCatalogAcrossKeystrokes() {
        catalog();

        sut.candidates("connect o", 9);
        sut.candidates("connect chat ", 13);
        sut.candidates("connect chat ollama ", 20);

        verify(providerClient, times(1)).catalog();
    }

    @Test
    void doesNotRetryUnreachableCatalogImmediately() {
        when(providerClient.catalog()).thenThrow(new RuntimeException("down"));

        sut.candidates("connect o", 9);
        sut.candidates("connect chat ", 13);

        verify(providerClient, times(1)).catalog();
    }
}