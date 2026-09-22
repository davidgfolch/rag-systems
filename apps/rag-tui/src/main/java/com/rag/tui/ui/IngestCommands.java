package com.rag.tui.ui;

import com.rag.common.core.tracing.TracePropagation;
import com.rag.contract.model.IngestStatusDTO;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_HASH;
import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.tui.ui.CommandDispatcher.orEmpty;
import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Handles the {@code add-file}, {@code add-folder} and {@code add-url} commands
 * handed to it by {@link CommandDispatcher}: file/folder/URL ingestion with
 * content-hash and source-URI deduplication plus asynchronous ingestion polling
 * so the shell stays responsive while ingest completes in the background.
 */
public class IngestCommands {

    private static final Logger log = LoggerFactory.getLogger(IngestCommands.class);
    private static final long POLL_MILLIS = 2_000;

    private final CommandDispatcher.RagClients clients;
    private final Prompter prompter;
    private final DuplicateFinder duplicates;
    private final Tracer tracer;

    public IngestCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                          DuplicateFinder duplicates, Tracer tracer) {
        this.clients = clients;
        this.prompter = prompter;
        this.duplicates = duplicates;
        this.tracer = tracer;
    }

    public String addFile(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("File path: "));
            if (path.isEmpty()) return "Usage: add-file <path>";
        }
        var file = clients.fileLoader().load(path);
        var fileName = file.metadata().get(FILE_NAME).toString();
        var duplicate = findByContentHash(file.metadata());
        if (duplicate.isPresent()) {
            var chosen = promptOverride(duplicate.get(), "file with identical content");
            if (chosen == null) return skipped(duplicate.get());
            clients.apiClient().deleteDocument(chosen.baseUrl(), chosen.documentId());
            log.info("Overriding duplicate document {} on {}", chosen.documentId(), chosen.moduleName());
        }
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        pollIngestUntilDone(job.getDocumentId(), tokenSink);
        return success(("Ingestion submitted for '%s' -> document %s. You can keep typing; I'll report when it completes.")
                .formatted(path, job.getDocumentId()));
    }

    public String addFolder(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("Folder path: "));
            if (path.isEmpty()) return "Usage: add-folder <path>";
        }
        var files = clients.fileLoader().loadFolder(path);
        if (files.isEmpty())
            return error("No ingestible files found in: " + path);
        int skippedDuplicates = 0;
        int submitted = 0;
        for (var file : files) {
            var duplicate = findByContentHash(file.metadata());
            if (shouldOverride(duplicate)) {
                var chosen = duplicate.get();
                clients.apiClient().deleteDocument(chosen.baseUrl(), chosen.documentId());
                log.info("Overriding duplicate document {} on {}", chosen.documentId(), chosen.moduleName());
            } else if (duplicate.isPresent()) {
                skippedDuplicates++;
                continue;
            }
            var fileName = file.metadata().get(FILE_NAME).toString();
            var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
            pollIngestUntilDone(job.getDocumentId(), tokenSink);
            submitted++;
        }
        return success("Submitted %d of %d files from '%s'; I'll report as each completes."
                .formatted(submitted, files.size(), path)
                + (skippedDuplicates > 0 ? " (" + skippedDuplicates + " duplicate(s) skipped)" : ""));
    }

    public String addUrl(String url) {
        if (url.isEmpty()) {
            url = orEmpty(prompter.prompt("URL: "));
            if (url.isEmpty()) return "Usage: add-url <url>";
        }
        var duplicate = duplicates.findBySource(url);
        if (duplicate.isPresent()) {
            var chosen = promptOverride(duplicate.get(), "web page " + url);
            if (chosen == null) return skipped(duplicate.get());
            clients.apiClient().deleteDocument(chosen.baseUrl(), chosen.documentId());
            log.info("Overriding duplicate document {} for {}", chosen.documentId(), url);
        }
        var res = clients.apiClient().ingestUrl(url);
        return success("Ingested %s -> document %s, %d chunks".formatted(url, res.getDocumentId(), res.getChunkCount()));
    }

    private Optional<DuplicateFinder.Match> findByContentHash(Map<String, Object> metadata) {
        var hash = contentHashOf(metadata);
        return hash == null ? Optional.<DuplicateFinder.Match>empty() : duplicates.findByContentHash(hash);
    }

    private static String contentHashOf(Map<String, Object> metadata) {
        Object value = metadata.get(CONTENT_HASH);
        return value == null ? null : value.toString();
    }

    private boolean shouldOverride(Optional<DuplicateFinder.Match> duplicate) {
        if (duplicate.isEmpty()) return false;
        return prompter.confirm("Duplicate file with identical content already ingested as document "
                + duplicate.get().documentId() + " on " + duplicate.get().moduleName()
                + ". Override? (y/N) ");
    }

    private DuplicateFinder.Match promptOverride(DuplicateFinder.Match match, String description) {
        return prompter.confirm("Duplicate %s already ingested as document %s on %s. Override? (y/N) "
                .formatted(description, match.documentId(), match.moduleName())) ? match : null;
    }

    private static String skipped(DuplicateFinder.Match match) {
        return "Skipped - already ingested as document " + match.documentId()
                + " on " + match.moduleName() + ".";
    }

    private void pollIngestUntilDone(String documentId, Consumer<String> tokenSink) {
        var span = tracer == null ? null : tracer.currentSpan();
        var poller = new Thread(() -> {
            try {
                var label = "document " + documentId;
                while (true) {
                    Thread.sleep(POLL_MILLIS);
                    var status = ingestStatus(documentId, span);
                    var state = status.getState();
                    if (IngestStatusDTO.StateEnum.COMPLETED == state) {
                        tokenSink.accept(success("Ingestion of " + label + " complete: " + status.getChunkCount() + " chunks.\n"));
                        return;
                    }
                    if (IngestStatusDTO.StateEnum.FAILED == state) {
                        tokenSink.accept(error("Ingestion of " + label + " failed: " + status.getMessage() + "\n"));
                        return;
                    }
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                tokenSink.accept(error("Ingestion of document " + documentId + " could not be checked: " + e.getMessage() + "\n"));
            }
        }, "rag-ingest-poll");
        poller.setDaemon(true);
        poller.start();
    }

    private IngestStatusDTO ingestStatus(String documentId, Span span) {
        return span == null ? clients.apiClient().ingestStatus(documentId)
                : TracePropagation.runWithSpan(tracer, span,
                        () -> clients.apiClient().ingestStatus(documentId));
    }
}