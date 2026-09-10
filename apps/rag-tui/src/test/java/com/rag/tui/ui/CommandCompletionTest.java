package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
                    TestModules.basic(),
                    TestModules.advanced()), TestModules.BASIC),
            providerClient);

    private void catalog() {
        when(providerClient.catalog())
                .thenReturn(new ModelCatalogDTO(List.of(
                        new ProviderModelDTO("openai", "gpt-4o", null, null, null, null, null),
                        new ProviderModelDTO("openrouter", "qwen", null, null, null, null, null),
                        new ProviderModelDTO("ollama", "phi4", null, null, null, null, null),
                        new ProviderModelDTO("cohere", "command", null, null, null, null, null)), "models.dev", Instant.now()));
    }

    @ParameterizedTest(name = "candidates(\"{0}\") shows providers matching the typed token")
    @CsvSource({
            "'connect o', 9",
            "'connect chat o', 14"
    })
    void showsProvidersMatchingTypedToken(String line, int cursor) {
        catalog();

        var result = sut.candidates(line, cursor);

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
    void containsFallbackMatchesSubcommandNamesWhenPrefixMisses() {
        catalog();

        var result = sut.candidates("connect at", 10);

        assertThat(result).containsExactly("catalog", "chat");
    }

    @ParameterizedTest(name = "candidates(\"{0}\") yields no candidates")
    @CsvSource({
            "'connect chat ', 13",
            "'connect chat openai ', 20",
            "'frobnicate x', 12"
    })
    void yieldsNoCandidates(String line, int cursor) {
        catalog();

        var result = sut.candidates(line, cursor);

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

        assertThat(result).containsExactly(TestModules.BASIC, TestModules.ADVANCED);
    }

    @Test
    void filtersModuleNamesByPrefix() {
        catalog();

        var result = sut.candidates("start rag-ad", 12);

        assertThat(result).containsExactly(TestModules.ADVANCED);
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