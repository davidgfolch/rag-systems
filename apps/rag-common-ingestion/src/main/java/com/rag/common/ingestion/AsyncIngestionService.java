package com.rag.common.ingestion;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.resilience.CapacityExceededException;
import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.core.tracing.TracePropagation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs {@link IngestionService#ingest} on a background thread so the calling
 * HTTP request (and therefore the thin TUI) is never blocked while a large
 * document is parsed, embedded chunk-by-chunk and stored. Each job transitions
 * PENDING -> RUNNING -> COMPLETED/FAILED and can be polled via
 * {@link #status(String)}. Errors are captured into the job rather than lost.
 */
public class AsyncIngestionService {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestionService.class);

    static final int MAX_RETAINED_JOBS = 1000;
    private static final Duration JOB_TTL = Duration.ofHours(1);
    public static final int DEFAULT_MAX_CONCURRENT = 2;
    public static final Duration DEFAULT_QUEUE_WAIT = Duration.ofMinutes(5);
    private static final String LIMITER_NAME = "ingestion";
    private static final String SATURATION_MESSAGE =
            "Ingestion capacity exhausted after waiting %d ms (max concurrent %d)";

    private final IngestionService delegate;
    private final ExecutorService executor;
    private final VectorStorePort preflight;
    private final Tracer tracer;
    private final ConcurrencyLimiter limiter;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger running = new AtomicInteger();

    public AsyncIngestionService(IngestionService delegate) {
        this(delegate, null, null, null);
    }

    public AsyncIngestionService(IngestionService delegate, ExecutorService executor) {
        this(delegate, null, executor, null);
    }

    public AsyncIngestionService(IngestionService delegate, VectorStorePort preflight, ExecutorService executor) {
        this(delegate, preflight, executor, null);
    }

    public AsyncIngestionService(IngestionService delegate, VectorStorePort preflight, ExecutorService executor, Tracer tracer) {
        this(delegate, preflight, executor, tracer, defaultLimiter());
    }

    public AsyncIngestionService(IngestionService delegate, VectorStorePort preflight, ExecutorService executor,
                                 Tracer tracer, ConcurrencyLimiter limiter) {
        this.delegate = delegate;
        this.preflight = preflight;
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        this.tracer = tracer;
        this.limiter = Objects.requireNonNull(limiter, "limiter must not be null");
    }

    private static ConcurrencyLimiter defaultLimiter() {
        return new ConcurrencyLimiter(LIMITER_NAME, DEFAULT_MAX_CONCURRENT, DEFAULT_QUEUE_WAIT);
    }

    /**
     * Submits a document for background ingestion and returns its id so callers
     * can poll {@link #status(String)}. Waits up to the configured queue wait
     * for capacity; the job starts as PENDING once a worker is scheduled and
     * becomes RUNNING when the worker picks it up.
     *
     * @throws CapacityExceededException when the gate stays saturated for the
     *         whole queue wait, so the job is never registered
     */
    public String submit(Document document) {
        return submit(document.getId(), job -> run(job, () -> delegate.ingest(document)));
    }

    /** Submits an already-parsed document, preserving its structure end to end. */
    public String submitParsed(ParsedDocument document) {
        return submit(document.documentId(), job -> run(job, () -> delegate.ingestParsed(document)));
    }

    private String submit(String documentId, Consumer<Job> task) {
        evictOldJobs();
        if (!limiter.tryEnter()) {
            throw saturated();
        }
        Job job = new Job(documentId);
        jobs.put(documentId, job);
        job.state = STATE_PENDING;
        log.info("Async ingestion submitted: document {}", documentId);
        Span span = tracer != null ? tracer.currentSpan() : null;
        try {
            executor.submit(() -> TracePropagation.runWithSpan(tracer, span, () -> task.accept(job)));
        } catch (RuntimeException e) {
            limiter.exit();
            failBeforeRun(job, e);
            throw e;
        }
        return documentId;
    }

    private CapacityExceededException saturated() {
        var message = SATURATION_MESSAGE.formatted(limiter.waitTimeout().toMillis(), limiter.maxConcurrent());
        log.warn("Async ingestion rejected: {}", message);
        return new CapacityExceededException(message);
    }

    private void failBeforeRun(Job job, RuntimeException failure) {
        job.state = STATE_FAILED;
        job.message = messageFor(failure);
        job.finishedAt = System.nanoTime();
        log.error("Async ingestion could not be scheduled for document {}", job.documentId, failure);
    }

    /**
     * Keeps the job registry bounded: completed jobs are dropped after the TTL,
     * then the oldest finished jobs are dropped until the size fits. Running
     * jobs are never evicted.
     */
    private void evictOldJobs() {
        if (jobs.size() < MAX_RETAINED_JOBS) {
            return;
        }
        var cutoff = System.nanoTime() - JOB_TTL.toNanos();
        jobs.entrySet().removeIf(e -> e.getValue().finishedAt > 0 && e.getValue().finishedAt < cutoff);
        if (jobs.size() < MAX_RETAINED_JOBS) {
            return;
        }
        var excess = jobs.size() - MAX_RETAINED_JOBS + 1;
        jobs.values().stream()
                .filter(j -> j.finishedAt > 0)
                .sorted(Comparator.comparingLong(j -> j.finishedAt))
                .limit(excess)
                .map(j -> j.documentId)
                .toList()
                .forEach(jobs::remove);
    }

    public JobStatus status(String documentId) {
        Job job = jobs.get(documentId);
        if (job == null) {
            return new JobStatus(documentId, STATE_FAILED, 0, "No such ingestion job");
        }
        return new JobStatus(job.documentId, job.state, job.chunkCount, job.message);
    }

    private void run(Job job, Supplier<IngestionService.IngestionResult> ingestion) {
        job.state = STATE_RUNNING;
        running.incrementAndGet();
        try {
            if (preflight != null) {
                log.info("Async ingestion preflight: vector store availability check for document {}", job.documentId);
                preflight.checkAvailable();
            }
            var result = ingestion.get();
            job.state = STATE_COMPLETED;
            job.chunkCount = result.chunkCount();
        } catch (Exception e) {
            job.state = STATE_FAILED;
            job.message = messageFor(e);
            log.error("Async ingestion failed for document {}", job.documentId, e);
        } finally {
            job.finishedAt = System.nanoTime();
            running.decrementAndGet();
            limiter.exit();
        }
    }

    private static String messageFor(Exception e) {
        if (e instanceof IngestionService.EmptyExtractionException) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    public int running() {
        return running.get();
    }

    private static final class Job {
        final String documentId;
        volatile String state;
        volatile int chunkCount;
        volatile String message;
        volatile long finishedAt;

        Job(String documentId) {
            this.documentId = documentId;
        }
    }

    public record JobStatus(String documentId, String state, int chunkCount, String message) {
    }
}
