package com.rag.common.ingestion;

import com.rag.common.core.domain.Document;
import com.rag.common.core.resilience.ConcurrencyLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.common.ingestion.AsyncIngestionService.STATE_COMPLETED;
import static com.rag.common.ingestion.AsyncIngestionService.STATE_FAILED;
import static com.rag.common.ingestion.AsyncIngestionService.STATE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncIngestionServiceConcurrencyTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(20);

    private final IngestionService delegate = mock(IngestionService.class);

    @Test
    void shouldFailJobWhenCapacityWaitExpires() {
        var captured = new AtomicReference<Runnable>();
        when(delegate.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 0));
        var service = new AsyncIngestionService(delegate, null, capturingExecutor(captured), null,
                new ConcurrencyLimiter("test", 1, SHORT_WAIT));
        var first = service.submit(new Document("d1", "content", Map.of()));
        assertThat(service.status(first).state()).isEqualTo(STATE_PENDING);

        var second = service.submit(new Document("d2", "content", Map.of()));

        assertThat(service.status(second).state()).isEqualTo(STATE_FAILED);
        assertThat(service.status(second).message()).contains("capacity exhausted");
        captured.get().run();
        assertThat(service.status(first).state()).isEqualTo(STATE_COMPLETED);
    }

    @Test
    void shouldReleasePermitWhenIngestionCompletes() {
        when(delegate.ingest(any())).thenReturn(new IngestionService.IngestionResult("d", 0));
        var service = new AsyncIngestionService(delegate, null, directExecutor(), null,
                new ConcurrencyLimiter("test", 1, SHORT_WAIT));

        var first = service.submit(new Document("d1", "content", Map.of()));
        var second = service.submit(new Document("d2", "content", Map.of()));

        assertThat(service.status(first).state()).isEqualTo(STATE_COMPLETED);
        assertThat(service.status(second).state()).isEqualTo(STATE_COMPLETED);
    }

    @Test
    void shouldReleasePermitWhenIngestionFails() {
        when(delegate.ingest(any())).thenThrow(new IllegalStateException("boom"));
        var service = new AsyncIngestionService(delegate, null, directExecutor(), null,
                new ConcurrencyLimiter("test", 1, SHORT_WAIT));

        var first = service.submit(new Document("d1", "content", Map.of()));
        var second = service.submit(new Document("d2", "content", Map.of()));

        assertThat(service.status(first).state()).isEqualTo(STATE_FAILED);
        assertThat(service.status(second).message()).contains("boom");
        assertThat(service.status(second).message()).doesNotContain("capacity exhausted");
    }

    private static ExecutorService directExecutor() {
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
                // no-op: work already ran inline
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }

    private static ExecutorService capturingExecutor(AtomicReference<Runnable> captured) {
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                captured.set(command);
            }

            @Override
            public void shutdown() {
                // no-op: this capturing executor never owns real worker threads
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return false;
            }
        };
    }
}
