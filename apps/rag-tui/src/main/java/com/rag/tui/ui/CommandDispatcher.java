package com.rag.tui.ui;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.common.core.services.FileDocumentLoader;
import com.rag.common.core.tracing.TracePropagation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_HASH;
import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final ModuleRegistry registry;
    private final ModuleLifecycleManager lifecycle;
    private final RagClients clients;
    private final Settings settings;
    private final CommandRegistry commandRegistry;
    private final Prompter prompter;
    private final DocumentLister documentLister;
    private final DuplicateFinder duplicates;
    private final Tracer tracer;

    static final String SPAN_COMMAND = "tui.command";

    public CommandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                             RagClients clients, Settings settings, CommandRegistry commandRegistry,
                             Prompter prompter) {
        this(registry, lifecycle, clients, settings, commandRegistry, prompter, null);
    }

    public CommandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                             RagClients clients, Settings settings, CommandRegistry commandRegistry,
                             Prompter prompter, Tracer tracer) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.clients = clients;
        this.settings = settings;
        this.commandRegistry = commandRegistry;
        this.prompter = prompter;
        this.documentLister = new DocumentLister(registry, clients.apiClient(), clients.healthClient());
        this.duplicates = new DuplicateFinder(registry, clients.apiClient(), clients.healthClient());
        this.tracer = tracer;
    }

    public String handle(String input, Consumer<String> tokenSink) {
        Span span = tracer == null ? null : tracer.nextSpan().name(SPAN_COMMAND).start();
        try {
            return TracePropagation.runWithSpan(tracer, span, () -> dispatch(input, tokenSink));
        } finally {
            if (span != null) span.end();
        }
    }

    private String dispatch(String input, Consumer<String> tokenSink) {
        var in = input.trim().toLowerCase();
        switch (in) {
            case "" -> {
                return "";
            }
            case "quit", "exit" -> throw new ShellExitException();
            case "help" -> {
                return commandRegistry.generateUsage();
            }
            default -> {
                var parts = in.split("\\s+", 2);
                var command = parts[0];
                var arg = parts.length > 1 ? parts[1].trim() : "";
                return dispatchCommand(command, arg, tokenSink);
            }
        }
    }

    private String dispatchCommand(String command, String arg, Consumer<String> tokenSink) {
        log.debug("Command: '{}'", command);
        try {
            return commandResult(command, arg, tokenSink);
        } catch (RestClientException e) {
            log.warn("Module unreachable on command '{}': {}", command, e.getMessage());
            return error("Module unreachable: " + e.getMessage());
        } catch (ChatGateway.ChatException e) {
            log.error("Chat failed on command '{}': {}", command, e.getMessage());
            return error("Chat error: " + e.getMessage());
        } catch (FileDocumentLoader.DocumentLoadException | ModuleLifecycleManager.StartException e) {
            log.error("Command '{}' failed: {}", command, e.getMessage());
            return error(e.getMessage());
        }
    }

    private String commandResult(String command, String arg, Consumer<String> tokenSink) {
        return switch (command) {
            case "modules" -> modules();
            case "use" -> use(arg);
            case "start" -> start(arg, tokenSink);
            case "stop" -> stop(arg);
            case "documents" -> documents();
            case "delete" -> delete(arg);
            case "add-file" -> addFile(arg, tokenSink);
            case "add-folder" -> addFolder(arg, tokenSink);
            case "add-url" -> addUrl(arg);
            case "ask" -> ask(arg, tokenSink);
            case "history" -> history();
            case "connect" -> new ConnectCommand(clients.providerClient(), prompter).execute(arg);
            default -> error("Unknown command. Type 'help' for usage.");
        };
    }

    /**
     * Active provider models for the startup banner. Empty when rag-provider is
     * unreachable, so the banner stays clean during development.
     */
    public String providerSummary() {
        try {
            return new ConnectCommand(clients.providerClient(), prompter).activeSpecs();
        } catch (RuntimeException e) {
            log.debug("Provider summary unavailable at startup: {}", e.getMessage());
            return "";
        }
    }

    private List<Prompter.Choice> moduleChoices() {
        return registry.modules().stream()
                .map(m -> new Prompter.Choice(m.name(), m.name()))
                .toList();
    }

    private String use(String name) {
        if (name.isEmpty()) {
            name = prompter.pick("Switch active module", moduleChoices()).orElse("");
            if (name.isEmpty()) return "";
        }
        return registry.activate(name)
                ? success("Active module: " + name)
                : error("Unknown module: " + name);
    }

    private String start(String name, Consumer<String> tokenSink) {
        if (name.isEmpty()) {
            name = prompter.pick("Start module", moduleChoices()).orElse("");
            if (name.isEmpty()) return "";
        }
        final String moduleName = name;
        return registry.find(moduleName)
                .map(m -> lifecycle.start(m)
                        ? waitForReady(m, tokenSink)
                        : "Module already running: " + moduleName)
                .orElse(error("Unknown module: " + moduleName));
    }

    private String stop(String name) {
        if (name.isEmpty()) {
            name = prompter.pick("Stop module", moduleChoices()).orElse("");
            if (name.isEmpty()) return "";
        }
        return lifecycle.stop(name) ? success("Stopped " + name) : "Module not running: " + name;
    }

    private String delete(String documentId) {
        if (documentId.isEmpty()) {
            documentId = pickDocumentId().orElse("");
            if (documentId.isEmpty()) return "";
        }
        final String id = documentId;
        for (Module module : registry.modules()) {
            if (!clients.healthClient().isUp(module.baseUrl())) continue;
            var docs = clients.apiClient().listDocuments(module.baseUrl());
            if (docs.stream().anyMatch(d -> id.equals(d.getDocumentId()))) {
                clients.apiClient().deleteDocument(module.baseUrl(), id);
                log.info("Deleted document {} from {}", id, module.name());
                return success("Deleted document " + id + " from " + module.name());
            }
        }
        return error("Document " + id + " not found on any reachable module.");
    }

    private Optional<String> pickDocumentId() {
        var choices = registry.modules().stream()
                .filter(m -> clients.healthClient().isUp(m.baseUrl()))
                .flatMap(m -> clients.apiClient().listDocuments(m.baseUrl()).stream())
                .map(d -> new Prompter.Choice(d.getTitle(), d.getDocumentId(),
                        d.getChunkCount() == null ? "" : d.getChunkCount() + " chunks"))
                .toList();
        return choices.isEmpty() ? Optional.empty() : prompter.pick("Delete document", choices);
    }

    private String addFile(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("File path: "));
            if (path.isEmpty()) return "Usage: add-file <path>";
        }
        var file = clients.fileLoader().load(path);
        var fileName = file.metadata().get(FILE_NAME).toString();
        var contentHash = contentHashOf(file.metadata());
        var duplicate = contentHash == null ? Optional.<DuplicateFinder.Match>empty()
                : duplicates.findByContentHash(contentHash);
        if (duplicate.isPresent()) {
            var chosen = promptOverride(duplicate.get(), "file with identical content");
            if (chosen == null) return skipped(duplicate.get());
            clients.apiClient().deleteDocument(chosen.baseUrl(), chosen.documentId());
            log.info("Overriding duplicate document {} on {}", chosen.documentId(), chosen.moduleName());
        }
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        var documentId = job.getDocumentId();
        pollIngestUntilDone(documentId, tokenSink);
        return success(("Ingestion submitted for '%s' -> document %s. You can keep typing; I'll report when it completes.")
                .formatted(path, documentId));
    }

    private static String contentHashOf(Map<String, Object> metadata) {
        Object value = metadata.get(CONTENT_HASH);
        return value == null ? null : value.toString();
    }

    private DuplicateFinder.Match promptOverride(DuplicateFinder.Match match, String description) {
        return prompter.confirm("Duplicate %s already ingested as document %s on %s. Override? (y/N) "
                .formatted(description, match.documentId(), match.moduleName())) ? match : null;
    }

    private static String skipped(DuplicateFinder.Match match) {
        return "Skipped - already ingested as document " + match.documentId()
                + " on " + match.moduleName() + ".";
    }

    private String addFolder(String path, Consumer<String> tokenSink) {
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
            var contentHash = contentHashOf(file.metadata());
            var duplicate = contentHash == null ? Optional.<DuplicateFinder.Match>empty()
                    : duplicates.findByContentHash(contentHash);
            if (duplicate.isPresent() && prompter.confirm("Duplicate file with identical content already ingested as document "
                    + duplicate.get().documentId() + " on " + duplicate.get().moduleName()
                    + ". Override? (y/N) ")) {
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

    private String addUrl(String url) {
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

    private String ask(String question, Consumer<String> tokenSink) {
        if (question.isEmpty()) {
            question = orEmpty(prompter.prompt("Question: "));
            if (question.isEmpty()) return "Usage: ask <question>";
        }
        clients.chatGateway().ask(question, settings.topK(), tokenSink);
        return "";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String modules() {
        var active = registry.active();
        var sb = new StringBuilder("Modules:\n");
        registry.modules().stream().map(m -> {
            boolean child = lifecycle.isRunning(m.name());
            boolean up = clients.healthClient().isUp(m.baseUrl());
            String state = (child || up) ? "running" : "stopped";
            String origin = moduleOrigin(up, child);
            String marker = m.name().equals(active.name()) ? " (active)" : "";
            return String.join(" ", m.name(), state + origin, marker, "\n");
        }).forEach(sb::append);
        return sb.toString();
    }

    private static String moduleOrigin(boolean up, boolean child) {
        if (!up) return "";
        return child ? " (child)" : " (external)";
    }

    private String documents() {
        return documentLister.list();
    }

    private String waitForReady(Module m, Consumer<String> tokenSink) {
        tokenSink.accept("Waiting for " + m.name() + " to become ready...\n");
        boolean ready = clients.healthClient().waitUntilUp(m.baseUrl(), settings.startTimeoutMs(), tokenSink);
        return ready ? success("Started %s (ready)".formatted(m.name()))
                : error(("Started %s but not ready after %ds - module is still booting or unhealthy; check docker/ollama and the module log, then retry.")
                .formatted(m.name(), settings.startTimeoutMs() / 1000));
    }

    private static final long POLL_MILLIS = 2_000;

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

    private String history() {
        var conversations = clients.memoryClient().conversations();
        if (conversations.isEmpty()) return "No conversations yet.";
        var sb = new StringBuilder("Conversations:\n");
        for (ConversationDTO conversation : conversations) {
            int count = clients.memoryClient().messages(conversation.getId()).size();
            sb.append(" - ").append(conversation.getId())
                    .append(" (").append(conversation.getTitle() == null ? "" : conversation.getTitle())
                    .append(", ").append(count).append(" messages)\n");
        }
        return sb.toString();
    }

    public record RagClients(RagApiClient apiClient, ChatGateway chatGateway, MemoryClient memoryClient,
                             FileDocumentLoader fileLoader, ModuleHealthClient healthClient,
                             ProviderClient providerClient) {
    }

    public record Settings(long startTimeoutMs, int topK, long chatTimeoutSeconds) {
    }
}
