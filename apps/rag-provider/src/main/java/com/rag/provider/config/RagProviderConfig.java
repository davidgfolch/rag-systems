package com.rag.provider.config;

import com.rag.provider.adapter.ProviderClientFactory;
import com.rag.provider.domain.ModelSpec;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import com.rag.provider.services.ChatService;
import com.rag.provider.services.EmbeddingService;
import com.rag.provider.services.ModelRouter;
import com.rag.provider.services.ProviderRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the provider service: seeds the profile registry + default active
 * models from environment and cold-starts the router with the configured
 * defaults (no network I/O happens until first use).
 */
@Configuration
public class RagProviderConfig {

    @Bean
    public ProviderRegistry providerRegistry(
            @Value("${rag.provider.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${rag.provider.openai.base-url:}") String openAiBaseUrl,
            @Value("${rag.provider.openai.api-key:}") String openAiApiKey) {
        return new ProviderRegistry(List.of(
                new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama", ollamaBaseUrl, ""),
                new ProviderProfile("openai", ProviderType.OPENAI, "OpenAI", openAiBaseUrl, openAiApiKey)));
    }

    @Bean
    public ProviderClientFactory providerClientFactory() {
        return new ProviderClientFactory();
    }

    @Bean
    public ModelRouter modelRouter(
            ProviderRegistry registry, ProviderClientFactory factory,
            @Value("${rag.provider.defaults.chat-provider:ollama}") String chatProvider,
            @Value("${rag.provider.ollama.chat-model:phi4}") String chatModel,
            @Value("${rag.provider.defaults.embedding-provider:ollama}") String embeddingProvider,
            @Value("${rag.provider.ollama.embedding-model:nomic-embed-text}") String embeddingModel) {
        var router = new ModelRouter(registry, factory,
                new ModelSpec(chatProvider, chatModel),
                new ModelSpec(embeddingProvider, embeddingModel));
        router.switchChat(router.currentChat());
        router.switchEmbedding(router.currentEmbedding());
        return router;
    }

    @Bean
    public ChatService chatService(ModelRouter router) {
        return new ChatService(router);
    }

    @Bean
    public EmbeddingService embeddingService(ModelRouter router) {
        return new EmbeddingService(router);
    }
}