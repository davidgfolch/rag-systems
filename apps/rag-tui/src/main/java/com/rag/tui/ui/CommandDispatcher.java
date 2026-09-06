package com.rag.tui.ui;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.common.services.FileDocumentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.util.function.Consumer;

public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final ModuleRegistry registry;
    private final ModuleLifecycleManager lifecycle;
    private final RagClients clients;
    private final Settings settings;
    private final CommandRegistry commandRegistry;

    public CommandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                             RagClients clients, Settings settings, CommandRegistry commandRegistry) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.clients = clients;
        this.settings = settings;
        this.commandRegistry = commandRegistry;
    }

    public CommandResult handle(String input, Consumer<String> tokenSink) {
        var trimmed = input.trim();
        if (trimmed.isEmpty()) return new CommandResult(commandRegistry.generateUsage(), false);

        var lower = trimmed.toLowerCase();
        if (lower.equals("quit") || lower.equals("exit")) {
            return new CommandResult(TerminalStyle.success("Bye."), true);
        }
        if (lower.equals("help")) return new CommandResult(commandRegistry.generateUsage(), false);

        var parts = trimmed.split("\\s+", 2);
        var arg = parts.length > 1 ? parts[1].trim() : "";
        var command = parts[0].toLowerCase();
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
                default -> new CommandResult(TerminalStyle.error("Unknown command. Type 'help' for usage."), false);
            };
        } catch (RestClientException e) {
            log.warn("Module unreachable on command '{}': {}", parts[0], e.getMessage());
            return new CommandResult(TerminalStyle.error("Module unreachable: " + e.getMessage()), false);
        } catch (ChatGateway.ChatException e) {
            log.error("Chat failed on command '{}': {}", parts[0], e.getMessage());
            return new CommandResult(TerminalStyle.error("Chat error: " + e.getMessage()), false);
        } catch (FileDocumentLoader.DocumentLoadException | ModuleLifecycleManager.StartException e) {
            log.error("Command '{}' failed: {}", parts[0], e.getMessage());
            return new CommandResult(TerminalStyle.error(e.getMessage()), false);
        }
    }

    private CommandResult modules() {
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
        return new CommandResult(sb.toString(), false);
    }

    private static String moduleOrigin(boolean up, boolean child) {
        if (!up) return "";
        return child ? " (child)" : " (external)";
    }

    private CommandResult documents() {
        var sb = new StringBuilder("Documents:\n");
        boolean any = false;
        for (Module m : registry.modules()) {
            if (clients.healthClient().isUp(m.baseUrl())) {
                any = true;
                appendModuleDocuments(sb, m);
            }
        }
        if (!any) {
            return new CommandResult("No rag-* modules are reachable. Start one first (e.g. 'start rag-basic').", false);
        }
        return new CommandResult(sb.toString(), false);
    }

    private void appendModuleDocuments(StringBuilder sb, Module m) {
        sb.append(" ").append(m.name()).append(" (").append(m.baseUrl()).append("):\n");
        var docs = clients.apiClient().listDocuments(m.baseUrl());
        if (docs.isEmpty()) {
            sb.append("   (no documents)\n");
            return;
        }
        for (DocumentSummaryDTO doc : docs) {
            appendDocumentSummary(sb, doc);
        }
    }

    private static void appendDocumentSummary(StringBuilder sb, DocumentSummaryDTO doc) {
        sb.append("   - ");
        var title = doc.getTitle();
        if (title == null || title.isBlank()) {
            title = doc.getDocumentId();
        }
        sb.append(title);
        if (doc.getChunkCount() != null) {
            sb.append(" (").append(doc.getChunkCount()).append(" chunks)");
        }
        if (doc.getDocumentId() != null && !doc.getDocumentId().equals(title)) {
            sb.append(" [").append(doc.getDocumentId()).append("]");
        }
        if (doc.getCreatedAt() != null) {
            sb.append(" ").append(doc.getCreatedAt());
        }
        sb.append("\n");
    }

    private CommandResult use(String name) {
        if (name.isEmpty()) return new CommandResult("Usage: use <module>", false);
        return registry.activate(name)
                ? new CommandResult(TerminalStyle.success("Active module: " + name), false)
                : new CommandResult(TerminalStyle.error("Unknown module: " + name), false);
    }

    private CommandResult delete(String documentId) {
        if (documentId.isEmpty()) return new CommandResult("Usage: delete <document-id>", false);
        clients.apiClient().deleteDocument(documentId);
        log.info("Deleted document {}", documentId);
        return new CommandResult(TerminalStyle.success("Deleted document " + documentId), false);
    }

    private CommandResult start(String name, Consumer<String> tokenSink) {
        return registry.find(name)
                .map(m -> lifecycle.start(m)
                        ? waitForReady(m, tokenSink)
                        : new CommandResult("Module already running: " + name, false))
                .orElse(new CommandResult(TerminalStyle.error("Unknown module: " + name), false));
    }

    private CommandResult waitForReady(Module m, Consumer<String> tokenSink) {
        tokenSink.accept("Waiting for " + m.name() + " to become ready...\n");
        boolean ready = clients.healthClient().waitUntilUp(m.baseUrl(), settings.startTimeoutMs(), tokenSink);
        return ready
                ? new CommandResult(TerminalStyle.success("Started %s (ready)".formatted(m.name())), false)
                : new CommandResult(TerminalStyle.error(("Started %s but not ready after %ds - module is still booting "
                        + "or unhealthy; check docker/ollama and the module log, then retry.")
                        .formatted(m.name(), settings.startTimeoutMs() / 1000)), false);
    }

    private CommandResult stop(String name) {
        boolean stopped = lifecycle.stop(name);
        return new CommandResult(stopped
                ? TerminalStyle.success("Stopped " + name)
                : "Module not running: " + name, false);
    }

    private CommandResult addFile(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) return new CommandResult("Usage: add-file <path>", false);
        var file = clients.fileLoader().load(path);
        var fileName = file.metadata().get("fileName").toString();
        var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        var documentId = job.getDocumentId();
        String message = TerminalStyle.success(("Ingestion submitted for '%s' -> document %s. You can keep typing; "
                + "I'll report when it completes.").formatted(path, documentId));
        pollIngestUntilDone(documentId, tokenSink);
        return new CommandResult(message, false);
    }

    private CommandResult addFolder(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) return new CommandResult("Usage: add-folder <path>", false);
        var files = clients.fileLoader().loadFolder(path);
        if (files.isEmpty()) {
            return new CommandResult(TerminalStyle.error("No ingestible files found in: " + path), false);
        }
        for (var file : files) {
            var fileName = file.metadata().get("fileName").toString();
            var job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
            pollIngestUntilDone(job.getDocumentId(), tokenSink);
        }
        return new CommandResult(TerminalStyle.success("Submitted %d files from '%s'; I'll report as each completes."
                .formatted(files.size(), path)), false);
    }

    private void pollIngestUntilDone(String documentId, Consumer<String> tokenSink) {
        var poller = new Thread(() -> {
            try {
                var label = "document " + documentId;
                while (true) {
                    Thread.sleep(POLL_MILLIS);
                    var status = clients.apiClient().ingestStatus(documentId);
                    var state = status.getState();
                    if (IngestStatusDTO.StateEnum.COMPLETED == state) {
                        tokenSink.accept(TerminalStyle.success("Ingestion of " + label + " complete: "
                                + status.getChunkCount() + " chunks.\n"));
                        return;
                    }
                    if (IngestStatusDTO.StateEnum.FAILED == state) {
                        tokenSink.accept(TerminalStyle.error("Ingestion of " + label + " failed: " + status.getMessage() + "\n"));
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                tokenSink.accept(TerminalStyle.error("Ingestion of document " + documentId
                        + " could not be checked: " + e.getMessage() + "\n"));
            }
        }, "rag-ingest-poll");
        poller.setDaemon(true);
        poller.start();
    }

    private static final long POLL_MILLIS = 2_000;

    private CommandResult addUrl(String url) {
        if (url.isEmpty()) return new CommandResult("Usage: add-url <url>", false);
        var response = clients.apiClient().ingestUrl(url);
        return new CommandResult(TerminalStyle.success("Ingested %s -> document %s, %d chunks"
                .formatted(url, response.getDocumentId(), response.getChunkCount())), false);
    }

    private CommandResult ask(String question, Consumer<String> tokenSink) {
        if (question.isEmpty()) return new CommandResult("Usage: ask <question>", false);
        clients.chatGateway().ask(question, settings.topK(), tokenSink);
        return new CommandResult("", false);
    }

    private CommandResult history() {
        var conversations = clients.memoryClient().conversations();
        if (conversations.isEmpty()) return new CommandResult("No conversations yet.", false);
        var sb = new StringBuilder("Conversations:\n");
        for (ConversationDTO conversation : conversations) {
            int count = clients.memoryClient().messages(conversation.getId()).size();
            sb.append(" - ").append(conversation.getId())
                    .append(" (").append(conversation.getTitle() == null ? "" : conversation.getTitle())
                    .append(", ").append(count).append(" messages)\n");
        }
        return new CommandResult(sb.toString(), false);
    }

    public record RagClients(RagApiClient apiClient, ChatGateway chatGateway, MemoryClient memoryClient,
                             FileDocumentLoader fileLoader, ModuleHealthClient healthClient) {}

    public record Settings(long startTimeoutMs, int topK, long chatTimeoutSeconds) {}
}
