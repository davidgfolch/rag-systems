package com.rag.tui.ui;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.contract.provider.ModelCapabilitiesDTO;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelLimitsDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.tui.client.ProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.rag.tui.testfixture.TestModelCatalogs.CLOUD_CHAT;
import static com.rag.tui.testfixture.TestModelCatalogs.LOCAL_CHAT;
import static com.rag.tui.testfixture.TestModelCatalogs.LOCAL_EMBED;
import static com.rag.tui.testfixture.TestModelCatalogs.PROVIDER_OLLAMA;
import static com.rag.tui.testfixture.TestModelCatalogs.SOURCE;

class ConnectCommandTest {

    private final ProviderClient client = mock(ProviderClient.class);
    private final Prompter prompter = mock(Prompter.class);
    private final ConnectCommand sut = new ConnectCommand(client, prompter);

    @Test
    void choosesProviderAndModelInteractivelyWhenBothMissing() {
        when(client.catalog()).thenReturn(catalog());
        when(prompter.pick(eq("Choose chat provider"), anyList())).thenReturn(Optional.of("openrouter"));
        when(prompter.pick(eq("Choose chat model for openrouter"), anyList()))
                .thenReturn(Optional.of(CLOUD_CHAT));

        var result = sut.execute("chat");

        assertThat(result).contains("Chat model switched", "openrouter/" + CLOUD_CHAT);
        verify(client).switchChat("openrouter", CLOUD_CHAT);
    }

    @Test
    void picksOnlyModelWhenProviderGiven() {
        when(client.catalog()).thenReturn(catalog());
        when(prompter.pick(eq("Choose embedding model for openrouter"), anyList()))
                .thenReturn(Optional.of("text-embedding-3-small"));

        var result = sut.execute("embedding openrouter");

        assertThat(result).contains("Embedding model switched", "openrouter/text-embedding-3-small");
        verify(client).switchEmbedding("openrouter", "text-embedding-3-small");
        verify(prompter, never()).pick(eq("Choose embedding provider"), anyList());
    }

    @Test
    void cancelsProviderPickerWithoutSwitching() {
        when(client.catalog()).thenReturn(catalog());
        when(prompter.pick(eq("Choose chat provider"), anyList())).thenReturn(Optional.empty());

        var result = sut.execute("chat");

        assertThat(result).isEmpty();
        verify(client, never()).switchChat(anyString(), anyString());
    }

    @Test
    void cancelsModelPickerWithoutSwitching() {
        when(client.catalog()).thenReturn(catalog());
        when(prompter.pick(eq("Choose chat provider"), anyList())).thenReturn(Optional.of("openrouter"));
        when(prompter.pick(eq("Choose chat model for openrouter"), anyList())).thenReturn(Optional.empty());

        var result = sut.execute("chat");

        assertThat(result).isEmpty();
        verify(client, never()).switchChat(anyString(), anyString());
    }

    @Test
    void keepsTypedArgsWithoutPrompts() {
        var result = sut.execute("chat openrouter " + CLOUD_CHAT);

        assertThat(result).contains("Chat model switched", "openrouter/" + CLOUD_CHAT);
        verify(client).switchChat("openrouter", CLOUD_CHAT);
        verify(prompter, never()).pick(anyString(), anyList());
    }

    @Test
    void reportsProviderRejectionInsteadOfModuleUnreachable() {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "Model 'openrouter/free' is not allowed by provider 'openrouter'".getBytes(StandardCharsets.UTF_8), null))
                .when(client).switchChat("openrouter", "openrouter/free");

        var result = sut.execute("chat openrouter openrouter/free");

        assertThat(result)
                .contains("Request rejected by rag-provider", "not allowed")
                .doesNotContain("Module unreachable");
        verify(client).switchChat("openrouter", "openrouter/free");
    }

    @Test
    void configuresUnknownProviderThenRetriesSwitch() {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "Unknown provider: openrouter. Available providers: [ollama, openai]".getBytes(StandardCharsets.UTF_8), null))
                .doNothing()
                .when(client).switchChat("openrouter", "openrouter/free");
        when(prompter.pick(eq("Provider type for 'openrouter'"), anyList()))
                .thenReturn(Optional.of("OPENAI_COMPATIBLE"));
        when(prompter.prompt(contains("Base URL"))).thenReturn("https://openrouter.ai/api/v1");
        when(prompter.prompt(contains("API key"))).thenReturn("sk-or-123");

        var result = sut.execute("chat openrouter openrouter/free");

        assertThat(result).contains("Chat model switched", "openrouter/openrouter/free");
        verify(client, times(2)).switchChat("openrouter", "openrouter/free");
        verify(client).configure(new ConfigureProviderRequest("openrouter", "OPENAI_COMPATIBLE",
                "openrouter", "https://openrouter.ai/api/v1", "sk-or-123"));
    }

    @Test
    void abortsSwitchWhenProviderSetupCancelled() {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "Unknown provider: openrouter".getBytes(StandardCharsets.UTF_8), null))
                .when(client).switchChat("openrouter", "openrouter/free");
        when(prompter.pick(anyString(), anyList())).thenReturn(Optional.empty());

        var result = sut.execute("chat openrouter openrouter/free");

        assertThat(result).isEmpty();
        verify(client, never()).configure(any());
        verify(client, times(1)).switchChat("openrouter", "openrouter/free");
    }

    @Test
    void reportsProviderModuleUnreachableForConnectionFailures() {
        doThrow(new ResourceAccessException("connection refused"))
                .when(client).switchChat("openrouter", CLOUD_CHAT);

        var result = sut.execute("chat openrouter " + CLOUD_CHAT);

        assertThat(result).contains("Provider module unreachable", "connection refused");
    }

    @Test
    void rendersActiveModelsForStartupBanner() {
        when(client.status()).thenReturn(new ProviderStatusDTO(
                new ModelSpecDTO(PROVIDER_OLLAMA, LOCAL_CHAT),
                new ModelSpecDTO(PROVIDER_OLLAMA, LOCAL_EMBED), PROVIDER_OLLAMA, 768));

        var result = sut.activeSpecs();

        assertThat(result)
                .contains("Chat model: " + PROVIDER_OLLAMA + "/" + LOCAL_CHAT)
                .contains("Embedding model: " + PROVIDER_OLLAMA + "/" + LOCAL_EMBED + " (dimension 768)");
    }

    private static ModelCatalogDTO catalog() {
        return new ModelCatalogDTO(List.of(
                new ProviderModelDTO(PROVIDER_OLLAMA, LOCAL_CHAT, "Phi-4", new ModelLimitsDTO(16384, 4096),
                        new ModelCapabilitiesDTO(true, false, false), null, null),
                new ProviderModelDTO("openrouter", CLOUD_CHAT, "GPT-4o", new ModelLimitsDTO(128000, 16384),
                        new ModelCapabilitiesDTO(false, true, true), null, null)),
                SOURCE, Instant.now());
    }
}
