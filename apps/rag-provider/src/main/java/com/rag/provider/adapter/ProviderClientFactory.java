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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Builds Spring AI chat/embedding clients from a {@link ProviderProfile} and a
 * model name. OpenAI-compatible providers are supported with zero new adapters
 * by pointing the OpenAI client at an arbitrary base URL.
 *
 * <p>Clients are cached per profile+model: each build creates a new HTTP client
 * (and therefore a new connection pool), so rebuilding on every request or
 * model switch would leak sockets. The cache is cleared when it grows past a
 * small bound, which hands in-flight requests to their existing instances and
 * lets the old ones be collected.
 */
public class ProviderClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderClientFactory.class);

    private static final int MAX_CACHED_CLIENTS = 64;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(240);

    private final Map<ClientKey, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final Map<ClientKey, EmbeddingModel> embeddingModels = new ConcurrentHashMap<>();
    private final Duration connTimeout;
    private final Duration readTimeout;

    public ProviderClientFactory() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public ProviderClientFactory(Duration connTimeout, Duration readTimeout) {
        this.connTimeout = requirePositive(connTimeout, "connectTimeout");
        this.readTimeout = requirePositive(readTimeout, "readTimeout");
    }

    public ChatModel chatModel(ProviderProfile profile, String model) {
        return cached(chatModels, ClientKey.of(profile, model), () -> buildChatModel(profile, model));
    }

    public EmbeddingModel embeddingModel(ProviderProfile profile, String model) {
        return cached(embeddingModels, ClientKey.of(profile, model), () -> buildEmbeddingModel(profile, model));
    }

    private ChatModel buildChatModel(ProviderProfile profile, String model) {
        return switch (profile.type()) {
            case OLLAMA -> ollamaChat(profile, model);
            case OPENAI, OPENAI_COMPATIBLE -> openAiChat(profile, model);
        };
    }

    private EmbeddingModel buildEmbeddingModel(ProviderProfile profile, String model) {
        return switch (profile.type()) {
            case OLLAMA -> ollamaEmbedding(profile, model);
            case OPENAI, OPENAI_COMPATIBLE -> openAiEmbedding(profile, model);
        };
    }

    private static <T> T cached(Map<ClientKey, T> cache, ClientKey key, Supplier<T> factory) {
        if (cache.size() >= MAX_CACHED_CLIENTS) {
            cache.clear();
        }
        return cache.computeIfAbsent(key, ignored -> factory.get());
    }

    private record ClientKey(ProviderType type, String baseUrl, String apiKey, String model) {
        static ClientKey of(ProviderProfile profile, String model) {
            return new ClientKey(profile.type(), profile.baseUrl(), profile.apiKey(), model);
        }
    }

    private ChatModel ollamaChat(ProviderProfile profile, String model) {
        var api = OllamaApi.builder().baseUrl(profile.baseUrl()).restClientBuilder(restClientBuilder()).build();
        var options = OllamaChatOptions.builder().model(model).build();
        log.debug("Built Ollama chat client for model {}", model);
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    private EmbeddingModel ollamaEmbedding(ProviderProfile profile, String model) {
        var api = OllamaApi.builder().baseUrl(profile.baseUrl()).restClientBuilder(restClientBuilder()).build();
        var options = OllamaEmbeddingOptions.builder().model(model).build();
        log.debug("Built Ollama embedding client for model {}", model);
        return OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    private ChatModel openAiChat(ProviderProfile profile, String model) {
        var api = openAiApi(profile);
        var options = OpenAiChatOptions.builder().model(model).build();
        log.debug("Built OpenAI chat client for model {} via {}", model,
                profile.baseUrl() == null || profile.baseUrl().isBlank() ? "default endpoint" : profile.baseUrl());
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private EmbeddingModel openAiEmbedding(ProviderProfile profile, String model) {
        var api = openAiApi(profile);
        var options = OpenAiEmbeddingOptions.builder().model(model).build();
        log.debug("Built OpenAI embedding client for model {} via {}", model,
                profile.baseUrl() == null || profile.baseUrl().isBlank() ? "default endpoint" : profile.baseUrl());
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private OpenAiApi openAiApi(ProviderProfile profile) {
        var builder = OpenAiApi.builder().apiKey(profile.apiKey()).restClientBuilder(restClientBuilder());
        var baseUrl = withoutApiVersion(profile.baseUrl());
        if (ProviderType.OPENAI_COMPATIBLE.equals(profile.type())
                || (baseUrl != null && !baseUrl.isBlank())) {
            return builder.baseUrl(baseUrl).build();
        }
        return builder.build();
    }

    private RestClient.Builder restClientBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toTimeoutMillis(connTimeout));
        requestFactory.setReadTimeout(toTimeoutMillis(readTimeout));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static int toTimeoutMillis(Duration timeout) {
        return Math.toIntExact(Math.min(timeout.toMillis(), Integer.MAX_VALUE));
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static String withoutApiVersion(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return baseUrl;
        var trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : baseUrl;
    }
}