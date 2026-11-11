package com.rag.common.ingestion.testfixture;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.IngestionService;
import io.micrometer.tracing.Tracer;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

/** Builds {@link AsyncIngestionService} instances through the production constructor. */
public final class TestAsyncIngestionServices {

    private static final String LIMITER_NAME = "ingestion";
    private static final int DEFAULT_MAX_CONCURRENT = 2;
    private static final Duration DEFAULT_QUEUE_WAIT = Duration.ofMinutes(5);

    private TestAsyncIngestionServices() {
    }

    public static AsyncIngestionService of(IngestionService delegate) {
        return new AsyncIngestionService(delegate, null, null, null, defaultLimiter());
    }

    public static AsyncIngestionService withExecutor(IngestionService delegate, ExecutorService executor) {
        return new AsyncIngestionService(delegate, null, executor, null, defaultLimiter());
    }

    public static AsyncIngestionService withPreflight(IngestionService delegate, VectorStorePort preflight) {
        return new AsyncIngestionService(delegate, preflight, null, null, defaultLimiter());
    }

    public static AsyncIngestionService withTracer(IngestionService delegate, ExecutorService executor,
                                                   Tracer tracer) {
        return new AsyncIngestionService(delegate, null, executor, tracer, defaultLimiter());
    }

    private static ConcurrencyLimiter defaultLimiter() {
        return new ConcurrencyLimiter(LIMITER_NAME, DEFAULT_MAX_CONCURRENT, DEFAULT_QUEUE_WAIT, null);
    }
}
