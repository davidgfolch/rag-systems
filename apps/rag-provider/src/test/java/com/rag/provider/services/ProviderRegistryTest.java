package com.rag.provider.services;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderProfileStore;
import com.rag.provider.domain.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRegistryTest {

    @Test
    void shouldSeedDefaultsAndFindById() {
        var registry = new ProviderRegistry(List.of(
                new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama", "http://localhost:11434", "")));

        assertThat(registry.find("ollama")).isPresent();
        assertThat(registry.find("nope")).isEmpty();
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void shouldSaveAndOverrideProfile() {
        var registry = new ProviderRegistry(List.of());

        registry.save(new ConfigureProviderRequest("glhf", "OPENAI_COMPATIBLE", "GLHF",
                "https://glhf.chat", "k"));
        registry.save(new ConfigureProviderRequest("glhf", "OPENAI_COMPATIBLE", "GLHF v2",
                "https://v2.glhf.chat", "k2"));

        assertThat(registry.all()).hasSize(1);
        assertThat(registry.find("glhf").orElseThrow().displayName()).isEqualTo("GLHF v2");
        assertThat(registry.find("glhf").orElseThrow().baseUrl()).isEqualTo("https://v2.glhf.chat");
    }

    @Test
    void shouldDefaultBlankTypeToOpenAiCompatible() {
        var registry = new ProviderRegistry(List.of());
        var saved = registry.save(new ConfigureProviderRequest("x", "  ", "X", "http://x", ""));

        assertThat(saved.type()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
    }

    @Test
    void shouldRejectUnknownType() {
        var registry = new ProviderRegistry(List.of());
        var request = new ConfigureProviderRequest("x", "bogus", "X", "http://x", "");
        assertThatThrownBy(() -> registry.save(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMergeStoredProfilesOverSeeds() {
        var store = new ProviderProfileStore() {
            @Override
            public List<ProviderProfile> load() {
                return List.of(new ProviderProfile("ollama", ProviderType.OPENAI_COMPATIBLE,
                        "Local v2", "http://localhost:11434", "key"));
            }

            @Override
            public void save(List<ProviderProfile> profiles) {
                // no-op: seeding store persists nothing
            }
        };
        var registry = new ProviderRegistry(List.of(
                new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama", "http://localhost:11434", "")),
                store);

        assertThat(registry.find("ollama").orElseThrow().type()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
    }

    @Test
    void shouldPersistEverySaveThroughTheStore() {
        var saved = new ArrayList<ProviderProfile>();
        var store = new ProviderProfileStore() {
            @Override
            public List<ProviderProfile> load() {
                return List.of();
            }

            @Override
            public void save(List<ProviderProfile> profiles) {
                saved.addAll(profiles);
            }
        };
        var registry = new ProviderRegistry(List.of(), store);

        registry.save(new ConfigureProviderRequest("glhf", "OPENAI_COMPATIBLE", "GLHF",
                "https://glhf.chat", "k"));

        assertThat(saved).extracting(ProviderProfile::id).containsExactly("glhf");
    }
}