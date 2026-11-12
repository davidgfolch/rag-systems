package com.rag.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.core.resilience.ResilienceMetrics;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.provider.adapter.ModelsDevCatalogClient;
import com.rag.provider.adapter.ProviderClientFactory;
import com.rag.provider.adapter.ProviderProfileFileStore;
import com.rag.provider.domain.ModelCatalogPort;
import com.rag.provider.domain.ModelSpec;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderProfileStore;
import com.rag.provider.domain.ProviderType;
import com.rag.provider.services.ChatService;
import com.rag.provider.services.EmbeddingService;
import com.rag.provider.services.ModelCatalogService;
import com.rag.provider.services.ModelRouter;
import com.rag.provider.services.OllamaEndpointResolver;
import com.rag.provider.services.ProviderRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Wires the provider service: seeds the profile registry + default active
 * models from environment and cold-starts the router with the configured
 * defaults (no network I/O happens until first use). The model catalog is
 * fetched lazily from models.dev, so booting stays offline.
 */
@Configuration
public class RagProviderConfig {

    @Bean
    public ProviderProfileStore providerProfileStore(
            ObjectMapper om,
            @Value("${rag.provider.profiles-file:data/provider-profiles.json}") String profilesFile) {
        return new ProviderProfileFileStore(om, Path.of(profilesFile));
    }

    @Bean
    public ProviderRegistry providerRegistry(
            ProviderProfileStore store,
            @Value("${rag.provider.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${rag.provider.openai.base-url:}") String openAiBaseUrl,
            @Value("${rag.provider.openai.api-key:}") String openAiApiKey,
            OllamaEndpointResolver ollamaEndpointResolver) {
        return new ProviderRegistry(List.of(
                new ProviderProfile("ollama", ProviderType.OLLAMA, "Ollama",
                        ollamaEndpointResolver.resolve(ollamaBaseUrl), ""),
                new ProviderProfile("openai", ProviderType.OPENAI, "OpenAI", openAiBaseUrl, openAiApiKey)),
                store);
    }

    @Bean
    public ProviderClientFactory providerClientFactory(
            @Value("${rag.provider.http.connect-timeout:10s}") Duration connectTimeout,
            @Value("${rag.provider.http.read-timeout:240s}") Duration readTimeout) {
        return new ProviderClientFactory(connectTimeout, readTimeout);
    }

    @Bean
    public RetryPolicy providerRetryPolicy(
            @Value("${rag.provider.http.retry.max-attempts:4}") int maxAttempts,
            @Value("${rag.provider.http.retry.initial-backoff:500ms}") Duration initialBackoff,
            @Value("${rag.provider.http.retry.max-backoff:10s}") Duration maxBackoff) {
        return new RetryPolicy(maxAttempts, initialBackoff, maxBackoff,
                RetryPolicy.DEFAULT_MULTIPLIER, RetryPolicy.DEFAULT_JITTER);
    }

    @Bean
    public ConcurrencyLimiter embeddingLimiter(
            @Value("${rag.provider.embedding.max-concurrent:2}") int maxConcurrent,
            @Value("${rag.provider.embedding.queue-wait:180s}") Duration queueWait,
            ObjectProvider<MeterRegistry> meterRegistry) {
        return ResilienceMetrics.of(meterRegistry.getIfAvailable())
                .limiter("provider-embedding", maxConcurrent, queueWait);
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
    public ModelCatalogPort modelCatalogPort(
            @Value("${rag.provider.catalog.url:https://models.dev/api.json}") String url,
            ObjectMapper om) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .build();
        return new ModelsDevCatalogClient(url, restClient, om);
    }

    @Bean
    public ModelCatalogService modelCatalogService(
            ModelCatalogPort modelCatalogPort,
            @Value("${rag.provider.catalog.url:https://models.dev/api.json}") String source,
            @Value("${rag.provider.catalog.ttl:86400}") long ttlSeconds,
            @Value("${rag.provider.catalog.enabled:true}") boolean enabled) {
        return new ModelCatalogService(modelCatalogPort, source,
                Duration.ofSeconds(ttlSeconds), enabled, Clock.systemUTC());
    }

    @Bean
    public ChatService chatService(ModelRouter router) {
        return new ChatService(router);
    }

    @Bean
    public EmbeddingService embeddingService(
            ModelRouter router,
            @Value("${rag.provider.embedding.batch-size:32}") int maxEmbeddingBatchSize,
            ConcurrencyLimiter embeddingLimiter, RetryPolicy providerRetryPolicy,
            ObjectProvider<MeterRegistry> meterRegistry) {
        return new EmbeddingService(router, maxEmbeddingBatchSize, embeddingLimiter, providerRetryPolicy,
                ResilienceMetrics.of(meterRegistry.getIfAvailable()).retryObserver("provider-embedding"));
    }
}