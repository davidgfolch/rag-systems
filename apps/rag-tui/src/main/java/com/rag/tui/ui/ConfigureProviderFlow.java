package com.rag.tui.ui;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.tui.client.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Walks the user through registering a provider rag-provider does not know yet:
 * type, base URL and (unless local) API key, then persists it via
 * {@code POST /api/provider/configure}. Returns whether the profile was added;
 * a cancelled type pick aborts without any change.
 */
public class ConfigureProviderFlow {

    private static final Logger log = LoggerFactory.getLogger(ConfigureProviderFlow.class);

    private final ProviderClient client;
    private final Prompter prompter;

    public ConfigureProviderFlow(ProviderClient client, Prompter prompter) {
        this.client = client;
        this.prompter = prompter;
    }

    /** Prompts for a new provider profile and registers it. */
    public boolean configure(String providerId) {
        log.info("Starting runtime configuration for provider '{}'", providerId);
        var type = askType(providerId);
        if (type == null) return false;
        var meta = providerMeta(providerId);
        String baseUrl = meta.baseUrl != null
                ? meta.baseUrl
                : prompter.prompt("Base URL for '" + providerId + "' (empty = provider default)").trim();
        String apiKey = type.equals("OLLAMA")
                ? ""
                : askApiKey(providerId, meta.apiKeyEnv);
        client.configure(new ConfigureProviderRequest(providerId, type, providerId, baseUrl, apiKey));
        return true;
    }

    private ProviderMeta providerMeta(String providerId) {
        try {
            var catalog = client.catalog();
            if (catalog == null) return ProviderMeta.NONE;
            return catalog.models().stream()
                    .filter(m -> m.providerId().equalsIgnoreCase(providerId))
                    .findFirst()
                    .map(m -> new ProviderMeta(m.baseUrl(), m.apiKeyEnv()))
                    .orElse(ProviderMeta.NONE);
        } catch (RuntimeException e) {
            log.warn("Could not read catalog metadata for '{}': {}", providerId, e.getMessage());
            return ProviderMeta.NONE;
        }
    }

    private String askApiKey(String providerId, String envVar) {
        String hint = envVar == null || envVar.isBlank() ? "" : " (" + envVar + ")";
        return prompter.prompt("API key for '" + providerId + "'" + hint + ", empty = none").trim();
    }

    private record ProviderMeta(String baseUrl, String apiKeyEnv) {
        private static final ProviderMeta NONE = new ProviderMeta(null, null);
    }

    private String askType(String providerId) {
        return prompter.pick("Provider type for '" + providerId + "'", List.of(
                new Prompter.Choice("OpenAI-compatible", "OPENAI_COMPATIBLE",
                        "Any OpenAI-shaped API (OpenRouter, GLHF, ...)"),
                new Prompter.Choice("OpenAI", "OPENAI", "api.openai.com"),
                new Prompter.Choice("Ollama (local)", "OLLAMA", "No API key needed")))
                .orElse(null);
    }
}