package com.rag.provider.testfixture;

import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.provider.services.EmbeddingService;
import com.rag.provider.services.ModelRouter;

import java.time.Duration;

/** Builds {@link EmbeddingService} instances through the production constructor. */
public final class TestEmbeddingServices {

    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int MAX_CONCURRENT = 2;
    private static final Duration QUEUE_WAIT = Duration.ofSeconds(5);
    private static final String LIMITER_NAME = "test-embedding";

    private TestEmbeddingServices() {
    }

    public static EmbeddingService of(ModelRouter router) {
        return withBatchSize(router, DEFAULT_BATCH_SIZE);
    }

    public static EmbeddingService withBatchSize(ModelRouter router, int batchSize) {
        return new EmbeddingService(router, batchSize, limiter(), RetryPolicy.noRetry(), RetryObserver.NOOP);
    }

    public static EmbeddingService withLimiter(ModelRouter router, ConcurrencyLimiter limiter,
                                                RetryPolicy retryPolicy) {
        return new EmbeddingService(router, DEFAULT_BATCH_SIZE, limiter, retryPolicy, RetryObserver.NOOP);
    }

    private static ConcurrencyLimiter limiter() {
        return new ConcurrencyLimiter(LIMITER_NAME, MAX_CONCURRENT, QUEUE_WAIT, null);
    }
}
