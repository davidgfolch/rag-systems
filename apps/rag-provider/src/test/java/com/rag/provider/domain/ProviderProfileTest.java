package com.rag.provider.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderProfileTest {

    @ParameterizedTest(name = "should reject id when id is \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectInvalidId(String id) {
        assertThatThrownBy(() -> new ProviderProfile(id, ProviderType.OLLAMA, "Ollama",
                "http://localhost:11434", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new ProviderProfile("ollama", null, "Ollama",
                "http://localhost:11434", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("type must not be null");
    }

    @Test
    void shouldKeepProvidedValuesWhenValid() {
        var profile = new ProviderProfile("glhf", ProviderType.OPENAI_COMPATIBLE, "GLHF",
                "https://glhf.chat", "k");
        assertThat(profile.id()).isEqualTo("glhf");
        assertThat(profile.type()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
        assertThat(profile.displayName()).isEqualTo("GLHF");
        assertThat(profile.baseUrl()).isEqualTo("https://glhf.chat");
        assertThat(profile.apiKey()).isEqualTo("k");
    }

    @Test
    void shouldAllowBlankBaseUrlAndApiKey() {
        var profile = new ProviderProfile("openai", ProviderType.OPENAI, "OpenAI", "", "");
        assertThat(profile.baseUrl()).isEmpty();
        assertThat(profile.apiKey()).isEmpty();
    }
}
