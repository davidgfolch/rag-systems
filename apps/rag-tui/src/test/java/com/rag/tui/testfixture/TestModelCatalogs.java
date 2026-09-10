package com.rag.tui.testfixture;

import com.rag.contract.provider.ModelCapabilitiesDTO;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelCostDTO;
import com.rag.contract.provider.ModelLimitsDTO;
import com.rag.contract.provider.ProviderModelDTO;

import java.time.Instant;
import java.util.List;

public final class TestModelCatalogs {

    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String LOCAL_CHAT = "phi4";
    public static final String LOCAL_EMBED = "nomic-embed-text";
    public static final String CLOUD_CHAT = "gpt-4o";
    public static final String SOURCE = "https://models.dev/api.json";

    private TestModelCatalogs() {
    }

    public static ModelCatalogDTO defaultCatalog() {
        return new ModelCatalogDTO(List.of(
                new ProviderModelDTO(PROVIDER_OLLAMA, LOCAL_CHAT, "Phi-4",
                        new ModelLimitsDTO(16384, 4096),
                        new ModelCapabilitiesDTO(true, false, false),
                        new ModelCostDTO(0, 0), null),
                new ProviderModelDTO(PROVIDER_OPENAI, CLOUD_CHAT, "GPT-4o",
                        new ModelLimitsDTO(128000, 16384),
                        new ModelCapabilitiesDTO(false, true, true),
                        new ModelCostDTO(2.5, 10.0), null)),
                SOURCE, Instant.parse("2024-01-01T00:00:00Z"));
    }

    public static ModelCatalogDTO empty() {
        return new ModelCatalogDTO(List.of(), SOURCE, Instant.parse("2024-01-01T00:00:00Z"));
    }

    public static ModelCatalogDTO withModels(List<ProviderModelDTO> models) {
        return new ModelCatalogDTO(models, SOURCE, Instant.parse("2024-01-01T00:00:00Z"));
    }
}