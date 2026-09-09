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
import com.rag.common.services.FileDocumentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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

    public CommandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                             RagClients clients, Settings settings, CommandRegistry commandRegistry,
                             Prompter prompter) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.clients = clients;
        this.settings = settings;
        this.commandRegistry = commandRegistry;
        this.prompter = prompter;
        this.documentLister = new DocumentLister(registry, clients.apiClient(), clients.healthClient());
    }

    public String handle(String input, Consumer<String> tokenSink) {
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
                log.debug("Command: '{}'", command);
                try {
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
                } catch (RestClientException e) {
                    log.warn("Module unreachable on command '{}': {}", parts[0], e.getMessage());
                    return error("Module unreachable: " + e.getMessage());
                } catch (ChatGateway.ChatException e) {
                    log.error("Chat failed on command '{}': {}", parts[0], e.getMessage());
                    return error("Chat error: " + e.getMessage());
                } catch (FileDocumentLoader.DocumentLoadException | ModuleLifecycleManager.StartException e) {
                    log.error("Command '{}' failed: {}", parts[0], e.getMessage());
                    return error(e.getMessage());
                }
            }
        }
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
            boolean found = clients.apiClient().listDocuments(module.baseUrl()).stream()
                    .anyMatch(d -> id.equals(d.getDocumentId()));
            if (found) {
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
        var fileName = file.metadata().get("fileName").toString();
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        var documentId = job.getDocumentId();
        pollIngestUntilDone(documentId, tokenSink);
        return success(("Ingestion submitted for '%s' -> document %s. You can keep typing; I'll report when it completes.")
                .formatted(path, documentId));
    }

    private String addFolder(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) {
            path = orEmpty(prompter.prompt("Folder path: "));
            if (path.isEmpty()) return "Usage: add-folder <path>";
        }
        var files = clients.fileLoader().loadFolder(path);
        if (files.isEmpty())
            return error("No ingestible files found in: " + path);
        for (var file : files) {
            var fileName = file.metadata().get("fileName").toString();
            var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
            pollIngestUntilDone(job.getDocumentId(), tokenSink);
        }
        return success("Submitted %d files from '%s'; I'll report as each completes.".formatted(files.size(), path));
    }

    private String addUrl(String url) {
        if (url.isEmpty()) {
            url = orEmpty(prompter.prompt("URL: "));
            if (url.isEmpty()) return "Usage: add-url <url>";
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
        var poller = new Thread(() -> {
            try {
                var label = "document " + documentId;
                while (true) {
                    Thread.sleep(POLL_MILLIS);
                    var status = clients.apiClient().ingestStatus(documentId);
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                tokenSink.accept(error("Ingestion of document " + documentId + " could not be checked: " + e.getMessage() + "\n"));
            }
        }, "rag-ingest-poll");
        poller.setDaemon(true);
        poller.start();
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
