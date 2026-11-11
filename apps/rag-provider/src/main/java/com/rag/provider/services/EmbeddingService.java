package com.rag.provider.services;

import com.rag.common.core.domain.FloatConversions;
import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.RetryableStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Embedding generation against the currently active embedding model.
 *
 * <p>Calls are bounded by a {@link ConcurrencyLimiter} so a burst of ingestion
 * jobs cannot overwhelm the backing model server, and transient failures
 * (timeouts, connection resets, 408/425/429/5xx) are retried with backoff.
 */
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ModelRouter router;
    private final int maxEmbeddingBatchSize;
    private final ConcurrencyLimiter limiter;
    private final RetryExecutor retry;

    public EmbeddingService(ModelRouter router, int maxEmbeddingBatchSize,
                            ConcurrencyLimiter limiter, RetryPolicy retryPolicy, RetryObserver observer) {
        this.router = router;
        this.maxEmbeddingBatchSize = requirePositive(maxEmbeddingBatchSize);
        this.limiter = Objects.requireNonNull(limiter, "limiter must not be null");
        this.retry = new RetryExecutor(Objects.requireNonNull(retryPolicy, "retryPolicy must not be null"),
                EmbeddingService::isRetryable, observer, Thread::sleep);
    }

    /**
     * Embeds with the provider's active model when {@code model} is blank, or
     * with that specific model so a module can embed one pipeline variant
     * differently without switching the global active model.
     */
    public List<List<Float>> embed(List<String> texts, String model) {
        var vectors = new ArrayList<List<Float>>();
        for (int from = 0; from < texts.size(); from += maxEmbeddingBatchSize) {
            var window = texts.subList(from, Math.min(texts.size(), from + maxEmbeddingBatchSize));
            vectors.addAll(embedWindow(window, model));
        }
        log.info("Embedded {} texts with model {} at dimension {}", texts.size(),
                model == null || model.isBlank() ? "(active)" : model,
                vectors.isEmpty() ? 0 : vectors.getFirst().size());
        return vectors;
    }

    private List<List<Float>> embedWindow(List<String> texts, String model) {
        log.debug("Embedding window of {} texts", texts.size());
        if (!limiter.tryEnter()) {
            throw new ProviderBusyException("Provider embedding capacity exhausted after waiting "
                    + limiter.waitTimeout().toMillis() + " ms (max concurrent "
                    + limiter.maxConcurrent() + ")");
        }
        try {
            var res = retry.call("embed " + texts.size() + " texts",
                    () -> router.embeddingModelFor(model).call(new EmbeddingRequest(texts, null)));
            return res.getResults().stream().map(Embedding::getOutput)
                    .map(FloatConversions::toFloatList).toList();
        } finally {
            limiter.exit();
        }
    }

    static boolean isRetryable(Throwable failure) {
        if (failure instanceof RestClientResponseException http) {
            return RetryableStatuses.isRetryable(http.getStatusCode().value());
        }
        return failure instanceof ResourceAccessException || hasIoCause(failure);
    }

    private static boolean hasIoCause(Throwable failure) {
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("embedding batch size must be positive");
        }
        return value;
    }
}