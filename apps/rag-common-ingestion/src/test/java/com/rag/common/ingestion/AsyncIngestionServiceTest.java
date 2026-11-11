package com.rag.common.ingestion;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.MetadataKeys;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.DocumentParser;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.core.tracing.TracePropagation;
import com.rag.common.ingestion.testfixture.TestAsyncIngestionServices;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.rag.common.ingestion.testfixture.TestParsedDocuments.text;

class AsyncIngestionServiceTest {

    private final DocumentParser parser = mock(DocumentParser.class);
    private final TextSplitter splitter = mock(TextSplitter.class);
    private final EmbeddingModelPort embeddingModel = mock(EmbeddingModelPort.class);
    private final VectorStorePort vectorStore = mock(VectorStorePort.class);

    private IngestionService ingestionService() {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Test
    void submitsAndTransitionsToCompletedWithChunkCount() {
        var delegate = ingestionService();
        when(parser.parse(any())).thenReturn(text("d1", "hello world"));
        var chunk = new Chunk("c1", "d1", "hello world", 0, Map.of());
        when(splitter.split(any())).thenReturn(List.of(chunk));
        when(embeddingModel.embed(List.of("hello world")))
                .thenReturn(List.of(new float[]{1.0f, 0.0f}));
        var service = TestAsyncIngestionServices.of(delegate);
        var id = service.submit(new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, new byte[]{1})));
        var status = await(service, id);
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.chunkCount()).isEqualTo(1);
    }

    @Test
    void marksFailedWhenIngestionThrows() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenThrow(new IllegalStateException("boom"));
        var service = TestAsyncIngestionServices.of(delegate);
        var id = service.submit(new Document("d1", "content", Map.of()));
        var status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("boom");
    }

    @Test
    void reportsEmptyExtractionMessageDirectly() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any()))
                .thenThrow(new IngestionService.EmptyExtractionException("d1"));
        var service = TestAsyncIngestionServices.withExecutor(delegate, Executors.newSingleThreadExecutor());
        var id = service.submit(new Document("d1", "  ", Map.of()));
        var status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).isEqualTo("No text could be extracted from document d1");
    }

    @Test
    void staysPendingUntilWorkerRunsJob() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 0));
        AtomicReference<Runnable> captured = new AtomicReference<>();
        var service = TestAsyncIngestionServices.withExecutor(delegate, capturingExecutor(captured));
        var id = service.submit(new Document("d1", "content", Map.of()));
        assertThat(service.status(id).state()).isEqualTo("PENDING");
        captured.get().run();
        assertThat(service.status(id).state()).isEqualTo("COMPLETED");
    }

    @Test
    void countsRunningJobsWhilePreflightIsBlocked() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 2));
        var store = mock(VectorStorePort.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(inv -> {
            entered.countDown();
            release.await();
            return null;
        }).when(store).checkAvailable();
        var service = TestAsyncIngestionServices.withPreflight(delegate, store);
        var id = service.submit(new Document("d1", "content", Map.of()));
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> AsyncIngestionService.STATE_RUNNING.equals(service.status(id).state()));
        release.countDown();
        var status = await(service, id);
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.chunkCount()).isEqualTo(2);
    }

    @Test
    void evictsTheOldestFinishedJobsWhenTheRegistryIsFull() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenReturn(new IngestionService.IngestionResult("d", 0));
        var service = TestAsyncIngestionServices.withExecutor(delegate, directExecutor());
        int total = AsyncIngestionService.MAX_RETAINED_JOBS + 5;
        for (int i = 0; i < total; i++) {
            service.submit(new Document("doc-" + i, "content", Map.of()));
        }
        assertThat(service.status("doc-0").message()).contains("No such ingestion job");
        assertThat(service.status("doc-" + (total - 1)).state()).isEqualTo(AsyncIngestionService.STATE_COMPLETED);
        assertThat(service.status("doc-5").state()).isEqualTo(AsyncIngestionService.STATE_COMPLETED);
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
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return false;
            }
        };
    }

    @Test
    void failsFastWithoutIngestingWhenVectorStoreUnavailable() {
        var delegate = mock(IngestionService.class);
        var store = mock(VectorStorePort.class);
        doThrow(new IllegalStateException("Vector store not available: DataAccessResourceFailureException: Connection refused"))
                .when(store).checkAvailable();
        var service = TestAsyncIngestionServices.withPreflight(delegate, store);
        var id = service.submit(new Document("d1", "content", Map.of()));
        var status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("Connection refused");
        verify(delegate, never()).ingest(any());
    }

    @Test
    void reportsFailedForUnknownJob() {
        var service = TestAsyncIngestionServices.of(mock(IngestionService.class));
        var status = service.status("nope");
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("No such ingestion job");
    }

    @Test
    void propagatesSubmitSpanOntoWorkerThread() {
        SimpleTracer tracer = new SimpleTracer();
        var delegate = ingestionService();
        when(parser.parse(any())).thenReturn(text("d1", "hello world"));
        when(splitter.split(any())).thenReturn(List.of(new Chunk("c1", "d1", "hello world", 0, Map.of())));
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            workerTraceId.set(MDC.get(TracePropagation.MDC_TRACE_ID));
            return List.of(new float[]{1.0f, 0.0f});
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var service = TestAsyncIngestionServices.withTracer(delegate, executor, tracer);
            var span = tracer.nextSpan().name("test-ingest").start();
            try (var _ = tracer.withSpan(span)) {
                service.submit(new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, new byte[]{1})));
            }
            var status = await(service, "d1");
            assertThat(status.state()).isEqualTo("COMPLETED");
            assertThat(workerTraceId.get()).isEqualTo(span.context().traceId());
        } finally {
            executor.shutdownNow();
        }
    }

    private static AsyncIngestionService.JobStatus await(AsyncIngestionService service, String id) {
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> isTerminal(service.status(id)));
        return service.status(id);
    }

    private static boolean isTerminal(AsyncIngestionService.JobStatus status) {
        return !"PENDING".equals(status.state()) && !"RUNNING".equals(status.state());
    }
}