package com.rag.tui.ui;

import com.rag.common.core.services.FileDocumentLoader;
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
import static java.util.Optional.ofNullable;

/**
 * Handles the {@code add-file} and {@code add-folder} commands handed to it by
 * {@link CommandDispatcher}: file/folder ingestion with content-hash
 * deduplication plus asynchronous ingestion polling so the shell stays
 * responsive while ingest completes in the background.
 */
public class IngestCommands {

    private static final Logger log = LoggerFactory.getLogger(IngestCommands.class);

    private final CommandDispatcher.RagClients clients;
    private final Prompter prompter;
    private final DuplicateFinder duplicates;
    private final AsyncIngestPoller poller;

    public IngestCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                          DuplicateFinder duplicates, Tracer tracer) {
        this(clients, prompter, duplicates, new AsyncIngestPoller(clients, tracer));
    }

    IngestCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                   DuplicateFinder duplicates, Tracer tracer, long pollMillis) {
        this(clients, prompter, duplicates, new AsyncIngestPoller(clients, tracer, pollMillis));
    }

    private IngestCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                           DuplicateFinder duplicates, AsyncIngestPoller poller) {
        this.clients = clients;
        this.prompter = prompter;
        this.duplicates = duplicates;
        this.poller = poller;
    }

    public String addFile(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("File path: "));
            if (path.isEmpty()) return "Usage: add-file <path>";
        }
        var file = clients.fileLoader().load(path);
        var fileName = file.metadata().get(FILE_NAME).toString();
        var contentHash = contentHashOf(file.metadata());
        if (poller.isInFlight(contentHash)) {
            return success("Skipped '%s' - an ingestion with identical content is already in progress.%n"
                    .formatted(path));
        }
        var duplicate = findByContentHash(file.metadata());
        if (duplicate.isPresent()) {
            var chosen = promptOverride(duplicate.get());
            if (chosen == null) return skipped(duplicate.get());
            deleteDocument(chosen);
        }
        if (!poller.markInFlight(contentHash)) {
            return success("Skipped '%s' - an ingestion with identical content is already in progress.%n"
                    .formatted(path));
        }
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        poller.start(job.getDocumentId(), contentHash, tokenSink);
        return success(("Ingestion submitted for '%s' -> document %s. You can keep typing; I'll report when it completes.")
                .formatted(path, job.getDocumentId()));
    }

    private void deleteDocument(DuplicateFinder.Match chosen) {
        clients.apiClient().deleteDocument(chosen.baseUrl(), chosen.documentId());
        log.info("Overriding duplicate document {} on {}", chosen.documentId(), chosen.moduleName());
    }

    public String addFolder(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("Folder path: "));
            if (path.isEmpty()) return "Usage: add-folder <path>";
        }
        var files = clients.fileLoader().loadFolder(path);
        if (files.isEmpty())
            return error("No ingestible files found in: " + path);
        var submitted = files.stream().map(file -> submit(file, tokenSink)).filter(Boolean::booleanValue).count();
        return success("Submitted %d of %d files from '%s'; I'll report as each completes."
                .formatted(submitted, files.size(), path)
                + (submitted < files.size() ? " (" + (files.size() - submitted) + " duplicate(s) skipped)" : ""));
    }

    private boolean submit(FileDocumentLoader.LoadedFile file, Consumer<String> tokenSink) {
        var contentHash = contentHashOf(file.metadata());
        if (shouldSkip(findByContentHash(file.metadata()).orElse(null), contentHash) || !poller.markInFlight(contentHash))
            return false;
        var fileName = file.metadata().get(FILE_NAME).toString();
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        poller.start(job.getDocumentId(), contentHash, tokenSink);
        return true;
    }

    private Optional<DuplicateFinder.Match> findByContentHash(Map<String, Object> metadata) {
        var hash = contentHashOf(metadata);
        return hash == null ? Optional.empty() : duplicates.findByContentHash(hash);
    }

    private static String contentHashOf(Map<String, Object> metadata) {
        Object value = metadata.get(CONTENT_HASH);
        return value == null ? null : value.toString();
    }

    private boolean shouldSkip(DuplicateFinder.Match duplicate, String contentHash) {
        if (poller.isInFlight(contentHash)) return true;
        if (ofNullable(duplicate).isEmpty()) return false;
        if (shouldOverride(duplicate)) {
            deleteDocument(duplicate);
            return false;
        }
        return true;
    }

    private boolean shouldOverride(DuplicateFinder.Match duplicate) {
        if (ofNullable(duplicate).isEmpty()) return false;
        return prompter.confirm("Duplicate file with identical content already ingested as document "
                + duplicate.documentId() + " on " + duplicate.moduleName()
                + ". Override? (y/N) ");
    }

    private DuplicateFinder.Match promptOverride(DuplicateFinder.Match match) {
        return prompter.confirm("Duplicate %s already ingested as document %s on %s. Override? (y/N) "
                .formatted("file with identical content", match.documentId(), match.moduleName())) ? match : null;
    }

    private static String skipped(DuplicateFinder.Match match) {
        return "Skipped - already ingested as document " + match.documentId()
                + " on " + match.moduleName() + ".";
    }
}