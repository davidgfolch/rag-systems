package com.rag.provider.adapter;

import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderClientFactoryTest {

    private final ProviderClientFactory factory = new ProviderClientFactory();

    @Test
    void shouldBuildOllamaClients() {
        var profile = new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama",
                "http://localhost:11434", "");

        assertThat(factory.chatModel(profile, "phi4")).isInstanceOf(OllamaChatModel.class);
        assertThat(factory.embeddingModel(profile, "nomic-embed-text")).isInstanceOf(OllamaEmbeddingModel.class);
    }

    @Test
    void shouldBuildOpenAiClients() {
        var profile = new ProviderProfile("openai", ProviderType.OPENAI, "OpenAI", "", "sk-x");

        assertThat(factory.chatModel(profile, "gpt-4o")).isInstanceOf(OpenAiChatModel.class);
        assertThat(factory.embeddingModel(profile, "text-embedding-3-small"))
                .isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void shouldBuildOpenAiCompatibleClientsAgainstCustomBaseUrl() {
        var profile = new ProviderProfile("glhf", ProviderType.OPENAI_COMPATIBLE, "GLHF",
                "https://glhf.chat", "k");

        assertThat(factory.chatModel(profile, "some-model")).isInstanceOf(OpenAiChatModel.class);
        assertThat(factory.embeddingModel(profile, "some-embedding")).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void shouldStripApiVersionFromBaseUrlOnce() {
        assertThat(ProviderClientFactory.withoutApiVersion("https://openrouter.ai/api/v1"))
                .isEqualTo("https://openrouter.ai/api");
        assertThat(ProviderClientFactory.withoutApiVersion("https://api.groq.com/openai/v1/"))
                .isEqualTo("https://api.groq.com/openai");
        assertThat(ProviderClientFactory.withoutApiVersion("https://v2.glhf.chat"))
                .isEqualTo("https://v2.glhf.chat");
        assertThat(ProviderClientFactory.withoutApiVersion(null)).isNull();
        assertThat(ProviderClientFactory.withoutApiVersion("")).isEqualTo("");
    }
}