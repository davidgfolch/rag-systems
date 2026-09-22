package com.rag.tui.ui;

import com.rag.common.core.tracing.TracePropagation;
import com.rag.contract.model.IngestStatusDTO;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Polls an asynchronous ingestion job to its terminal state on a background
 * daemon thread, reporting progress through the token sink. Also tracks the
 * content hashes of jobs still in flight so re-ingesting identical content is
 * skipped until the first submission finishes - closing the add-file/add-folder
 * dedup window that otherwise duplicates documents in the vector store.
 */
final class AsyncIngestPoller {

    private static final String THREAD_NAME = "rag-ingest-poll";
    private static final long DEFAULT_POLL_MILLIS = 2_000;

    private final CommandDispatcher.RagClients clients;
    private final Tracer tracer;
    private final long pollMillis;
    private final Set<String> inFlightHashes = ConcurrentHashMap.newKeySet();

    AsyncIngestPoller(CommandDispatcher.RagClients clients, Tracer tracer) {
        this(clients, tracer, DEFAULT_POLL_MILLIS);
    }

    AsyncIngestPoller(CommandDispatcher.RagClients clients, Tracer tracer, long pollMillis) {
        this.clients = clients;
        this.tracer = tracer;
        this.pollMillis = pollMillis;
    }

    boolean isInFlight(String contentHash) {
        return contentHash != null && inFlightHashes.contains(contentHash);
    }

    boolean markInFlight(String contentHash) {
        return contentHash == null || inFlightHashes.add(contentHash);
    }

    void start(String documentId, String contentHash, Consumer<String> tokenSink) {
        var span = tracer == null ? null : tracer.currentSpan();
        var poller = new Thread(() -> {
            try {
                var label = "document " + documentId;
                while (true) {
                    Thread.sleep(pollMillis);
                    var status = status(documentId, span);
                    var state = status.getState();
                    if (IngestStatusDTO.StateEnum.COMPLETED == state) {
                        clearInFlight(contentHash);
                        tokenSink.accept(success("Ingestion of " + label + " complete: " + status.getChunkCount() + " chunks.\n"));
                        return;
                    }
                    if (IngestStatusDTO.StateEnum.FAILED == state) {
                        clearInFlight(contentHash);
                        tokenSink.accept(error("Ingestion of " + label + " failed: " + status.getMessage() + "\n"));
                        return;
                    }
                }
            } catch (InterruptedException _) {
                clearInFlight(contentHash);
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                clearInFlight(contentHash);
                tokenSink.accept(error("Ingestion of document " + documentId + " could not be checked: " + e.getMessage() + "\n"));
            }
        }, THREAD_NAME);
        poller.setDaemon(true);
        poller.start();
    }

    private void clearInFlight(String contentHash) {
        if (contentHash != null) {
            inFlightHashes.remove(contentHash);
        }
    }

    private IngestStatusDTO status(String documentId, Span span) {
        return span == null ? clients.apiClient().ingestStatus(documentId)
                : TracePropagation.runWithSpan(tracer, span,
                        () -> clients.apiClient().ingestStatus(documentId));
    }
}