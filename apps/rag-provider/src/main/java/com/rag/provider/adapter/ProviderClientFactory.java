package com.rag.provider.adapter;

import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Builds Spring AI chat/embedding clients from a {@link ProviderProfile} and a
 * model name. OpenAI-compatible providers are supported with zero new adapters
 * by pointing the OpenAI client at an arbitrary base URL.
 */
public class ProviderClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderClientFactory.class);

    public ChatModel chatModel(ProviderProfile profile, String model) {
        return switch (profile.type()) {
            case OLLAMA -> ollamaChat(profile, model);
            case OPENAI, OPENAI_COMPATIBLE -> openAiChat(profile, model);
        };
    }

    public EmbeddingModel embeddingModel(ProviderProfile profile, String model) {
        return switch (profile.type()) {
            case OLLAMA -> ollamaEmbedding(profile, model);
            case OPENAI, OPENAI_COMPATIBLE -> openAiEmbedding(profile, model);
        };
    }

    private static ChatModel ollamaChat(ProviderProfile profile, String model) {
        var api = OllamaApi.builder().baseUrl(profile.baseUrl()).build();
        var options = OllamaChatOptions.builder().model(model).build();
        log.debug("Built Ollama chat client for model {}", model);
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    private static EmbeddingModel ollamaEmbedding(ProviderProfile profile, String model) {
        var api = OllamaApi.builder().baseUrl(profile.baseUrl()).build();
        var options = OllamaEmbeddingOptions.builder().model(model).build();
        log.debug("Built Ollama embedding client for model {}", model);
        return OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    private static ChatModel openAiChat(ProviderProfile profile, String model) {
        var api = openAiApi(profile);
        var options = OpenAiChatOptions.builder().model(model).build();
        log.debug("Built OpenAI chat client for model {} via {}", model,
                profile.baseUrl() == null || profile.baseUrl().isBlank() ? "default endpoint" : profile.baseUrl());
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private static EmbeddingModel openAiEmbedding(ProviderProfile profile, String model) {
        var api = openAiApi(profile);
        var options = OpenAiEmbeddingOptions.builder().model(model).build();
        log.debug("Built OpenAI embedding client for model {} via {}", model,
                profile.baseUrl() == null || profile.baseUrl().isBlank() ? "default endpoint" : profile.baseUrl());
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private static OpenAiApi openAiApi(ProviderProfile profile) {
        var builder = OpenAiApi.builder().apiKey(profile.apiKey());
        var baseUrl = withoutApiVersion(profile.baseUrl());
        if (ProviderType.OPENAI_COMPATIBLE.equals(profile.type())
                || (baseUrl != null && !baseUrl.isBlank())) {
            return builder.baseUrl(baseUrl).build();
        }
        return builder.build();
    }

    static String withoutApiVersion(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return baseUrl;
        var trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : baseUrl;
    }
}