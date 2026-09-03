package com.rag.common.services;

import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final IngestionService delegate;
    private final ExecutorService executor;
    private final VectorStore preflight;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public AsyncIngestionService(IngestionService delegate) {
        this(delegate, null, null);
    }

    public AsyncIngestionService(IngestionService delegate, ExecutorService executor) {
        this(delegate, null, executor);
    }

    public AsyncIngestionService(IngestionService delegate, VectorStore preflight, ExecutorService executor) {
        this.delegate = delegate;
        this.preflight = preflight;
        this.executor = executor != null ? executor : Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "rag-ingest");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Submits a document for background ingestion and returns its id so callers
     * can poll {@link #status(String)}. Returns immediately; the job state
     * starts as PENDING and becomes RUNNING once the worker thread picks it up.
     */
    public String submit(Document document) {
        Job job = new Job(document.getId());
        jobs.put(document.getId(), job);
        job.state = STATE_PENDING;
        log.info("Async ingestion submitted: document {}", document.getId());
        executor.submit(() -> run(job, document));
        return document.getId();
    }

    public JobStatus status(String documentId) {
        Job job = jobs.get(documentId);
        if (job == null) {
            return new JobStatus(documentId, STATE_FAILED, 0, "No such ingestion job");
        }
        return new JobStatus(job.documentId, job.state, job.chunkCount, job.message);
    }

    private void run(Job job, Document document) {
        job.state = STATE_RUNNING;
        try {
            if (preflight != null) {
                log.info("Async ingestion preflight: vector store availability check for document {}", document.getId());
                preflight.checkAvailable();
            }
            IngestionService.IngestionResult result = delegate.ingest(document);
            job.state = STATE_COMPLETED;
            job.chunkCount = result.chunkCount();
        } catch (Exception e) {
            job.state = STATE_FAILED;
            job.message = messageFor(e);
            log.error("Async ingestion failed for document {}", document.getId(), e);
        }
    }

    private static String messageFor(Exception e) {
        if (e instanceof IngestionService.EmptyExtractionException) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    public int running() {
        return (int) jobs.values().stream().filter(j -> STATE_RUNNING.equals(j.state)).count();
    }

    private static final class Job {
        final String documentId;
        volatile String state;
        volatile int chunkCount;
        volatile String message;

        Job(String documentId) {
            this.documentId = documentId;
        }
    }

    public record JobStatus(String documentId, String state, int chunkCount, String message) {
    }
}
