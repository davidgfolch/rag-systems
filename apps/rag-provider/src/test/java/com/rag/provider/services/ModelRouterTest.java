package com.rag.provider.services;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.provider.adapter.ProviderClientFactory;
import com.rag.provider.domain.ModelSpec;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelRouterTest {

    @Mock private ProviderClientFactory factory;
    @Mock private ChatModel chatModel;
    @Mock private EmbeddingModel embeddingModel;

    private static final ModelSpec CHAT_SPEC = new ModelSpec("ollama", "phi4");
    private static final ModelSpec EMBED_SPEC = new ModelSpec("ollama", "nomic-embed-text");

    private ModelRouter newRouter() {
        var registry = new ProviderRegistry(List.of(
                new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama",
                        "http://localhost:11434", "")));
        return new ModelRouter(registry, factory, CHAT_SPEC, EMBED_SPEC);
    }

    @Test
    void shouldSwitchModelsAndResolveDimensionFromModel() {
        when(factory.chatModel(any(), anyString())).thenReturn(chatModel);
        when(factory.embeddingModel(any(), anyString())).thenReturn(embeddingModel);
        when(embeddingModel.dimensions()).thenReturn(768);

        var router = newRouter();
        router.switchChat(new ModelSpec("ollama", "qwen3"));
        router.switchEmbedding(EMBED_SPEC);

        assertThat(router.currentChat().model()).isEqualTo("qwen3");
        assertThat(router.chatModel()).isSameAs(chatModel);
        assertThat(router.embeddingModel()).isSameAs(embeddingModel);
        assertThat(router.embeddingDimension()).isEqualTo(768);
        assertThat(router.status().chat().model()).isEqualTo("qwen3");
        assertThat(router.status().embedding().model()).isEqualTo("nomic-embed-text");
        assertThat(router.status().embeddingDimension()).isEqualTo(768);
    }

    @Test
    void shouldResolveDimensionByProbeWhenModelHasNone() {
        when(factory.embeddingModel(any(), anyString())).thenReturn(embeddingModel);
        when(embeddingModel.dimensions()).thenReturn(-1);
        when(embeddingModel.embed("probe")).thenReturn(new float[1536]);

        var router = newRouter();
        router.switchEmbedding(EMBED_SPEC);

        assertThat(router.embeddingDimension()).isEqualTo(1536);
    }

    @Test
    void shouldRejectUnknownProviderWhenSwitching() {
        var router = newRouter();
        var unknown = new ModelSpec("nope", "x");
        assertThatThrownBy(() -> router.switchChat(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown provider: nope");
    }

    @Test
    void shouldRebuildActiveModelsAfterConfigure() {
        when(factory.chatModel(any(), anyString())).thenReturn(chatModel);
        when(factory.embeddingModel(any(), anyString())).thenReturn(embeddingModel);

        var router = newRouter();
        router.configure(new ConfigureProviderRequest("ollama", "OLLAMA", "Ollama",
                "http://localhost:11434", ""));

        verify(factory).chatModel(any(), anyString());
        verify(factory).embeddingModel(any(), anyString());
        assertThat(router.currentChat()).isEqualTo(CHAT_SPEC);
    }

    @Test
    void shouldNotRebuildWhenConfiguringUnrelatedProvider() {
        var router = newRouter();
        router.configure(new ConfigureProviderRequest("glhf", "OPENAI_COMPATIBLE", "GLHF",
                "https://glhf.chat", "k"));

        assertThat(router.currentChat()).isEqualTo(CHAT_SPEC);
    }
}