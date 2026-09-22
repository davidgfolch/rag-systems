package com.rag.tui.ui;

import com.rag.common.core.services.FileDocumentLoader;
import com.rag.contract.provider.ModelCapabilitiesDTO;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelLimitsDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import org.awaitility.Awaitility;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.rag.tui.testfixture.TestModules.BASIC;
import static com.rag.tui.testfixture.TestModules.advanced;
import static com.rag.tui.testfixture.TestModules.basic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for {@link CommandDispatcher} command tests: the module
 * registry, mocked clients, the dispatcher under test and helpers used across
 * the per-command test classes.
 */
abstract class AbstractCommandDispatcherTest {

    protected final ModuleRegistry registry = new ModuleRegistry(
            List.of(basic(), advanced()), BASIC);
    protected final ModuleLifecycleManager lifecycle = mock(ModuleLifecycleManager.class);
    protected final RagApiClient apiClient = mock(RagApiClient.class);
    protected final ChatGateway chatGateway = mock(ChatGateway.class);
    protected final MemoryClient memoryClient = mock(MemoryClient.class);
    protected final FileDocumentLoader fileLoader = mock(FileDocumentLoader.class);
    protected final ModuleHealthClient healthClient = mock(ModuleHealthClient.class);
    protected final ProviderClient providerClient = mock(ProviderClient.class);
    protected final CommandRegistry commandRegistry = new CommandRegistry();
    protected final Prompter prompter = mock(Prompter.class);
    protected final CommandDispatcher sut = new CommandDispatcher(registry, lifecycle,
            new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient,
                    providerClient),
            new CommandDispatcher.Settings(10_000, 4, 60), commandRegistry, prompter);

    protected String handle(String input) {
        return sut.handle(input, token -> {});
    }

    protected static void await(List<String> tokens, String needle, int timeoutSeconds) {
        Awaitility.await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    protected void mockStatus() {
        when(providerClient.status()).thenReturn(new ProviderStatusDTO(
                new ModelSpecDTO("ollama", "phi4"),
                new ModelSpecDTO("ollama", "nomic-embed-text"), "ollama", 768));
    }

    protected static ModelCatalogDTO catalog(Instant fetchedAt) {
        return new ModelCatalogDTO(List.of(
                new ProviderModelDTO("ollama", "phi4", "Phi-4", new ModelLimitsDTO(16384, 4096),
                        new ModelCapabilitiesDTO(true, false, false), null, null),
                new ProviderModelDTO("openai", "gpt-4o", "GPT-4o", new ModelLimitsDTO(128000, 16384),
                        new ModelCapabilitiesDTO(false, true, true), null, null)),
                "https://models.dev/api.json", fetchedAt);
    }
}