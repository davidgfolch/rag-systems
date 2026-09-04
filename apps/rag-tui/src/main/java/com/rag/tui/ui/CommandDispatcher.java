package com.rag.tui.ui;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.common.services.FileDocumentLoader;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Consumer;

/**
 * Parses command lines and routes them to the active rag-module over REST/WS.
 * Pure routing and terminal formatting: no RAG logic runs here.
 */
public class CommandDispatcher {

    private static final String USAGE = """
            Available commands:
              help                  show this help
              modules               list known rag-* modules
              use <module>          switch the active module
              start <module>        start a module as a child process
              stop <module>         stop a running module
              documents             list ingested documents from every reachable rag-* module
              add-file <path>       ingest a local document into the active module
              add-url <url>         ingest a web page via the active module
              ask <question>        stream a chat answer (Ctrl+C cancels via socket close)
              history               show conversation history from rag-memory
              quit                  exit the terminal
            """;

    private final ModuleRegistry registry;
    private final ModuleLifecycleManager lifecycle;
    private final RagClients clients;
    private final Settings settings;

    public CommandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle, RagClients clients, Settings settings) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.clients = clients;
        this.settings = settings;
    }

    public CommandResult handle(String input, Consumer<String> tokenSink) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new CommandResult(USAGE, false);

        String lower = trimmed.toLowerCase();
        if (lower.equals("quit") || lower.equals("exit")) return new CommandResult("Bye.", true);
        if (lower.equals("help")) return new CommandResult(USAGE, false);

        String[] parts = trimmed.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1].trim() : "";
        try {
            return switch (parts[0].toLowerCase()) {
                case "modules" -> modules();
                case "use" -> use(arg);
                case "start" -> start(arg, tokenSink);
                case "stop" -> stop(arg);
                case "documents" -> documents();
                case "add-file" -> addFile(arg, tokenSink);
                case "add-url" -> addUrl(arg);
                case "ask" -> ask(arg, tokenSink);
                case "history" -> history();
                default -> new CommandResult("Unknown command. Type 'help' for usage.\n\n" + USAGE, false);
            };
        } catch (RestClientException e) {
            return new CommandResult("Module unreachable: " + e.getMessage(), false);
        } catch (ChatGateway.ChatException e) {
            return new CommandResult("Chat error: " + e.getMessage(), false);
        } catch (FileDocumentLoader.DocumentLoadException | ModuleLifecycleManager.StartException e) {
            return new CommandResult(e.getMessage(), false);
        }
    }

    private CommandResult modules() {
        Module active = registry.active();
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
        List<DocumentSummaryDTO> docs = clients.apiClient().listDocuments(m.baseUrl());
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
        String title = doc.getTitle();
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
                ? new CommandResult("Active module: " + name, false)
                : new CommandResult("Unknown module: " + name, false);
    }

    private CommandResult start(String name, Consumer<String> tokenSink) {
        return registry.find(name)
                .map(m -> lifecycle.start(m)
                        ? waitForReady(m, tokenSink)
                        : new CommandResult("Module already running: " + name, false))
                .orElse(new CommandResult("Unknown module: " + name, false));
    }

    private CommandResult waitForReady(Module m, Consumer<String> tokenSink) {
        tokenSink.accept("Waiting for " + m.name() + " to become ready...\n");
        boolean ready = clients.healthClient().waitUntilUp(m.baseUrl(), settings.startTimeoutMs(), tokenSink);
        return ready
                ? new CommandResult("Started %s (ready)".formatted(m.name()), false)
                : new CommandResult(("Started %s but not ready after %ds - module is still booting "
                        + "or unhealthy; check docker/ollama and the module log, then retry.")
                        .formatted(m.name(), settings.startTimeoutMs() / 1000), false);
    }

    private CommandResult stop(String name) {
        boolean stopped = lifecycle.stop(name);
        return new CommandResult(stopped ? "Stopped " + name : "Module not running: " + name, false);
    }

    private CommandResult addFile(String path, Consumer<String> tokenSink) {
        if (path.isEmpty()) return new CommandResult("Usage: add-file <path>", false);
        FileDocumentLoader.LoadedFile file = clients.fileLoader().load(path);
        String fileName = file.metadata().get("fileName").toString();
        IngestJobResponse job = clients.apiClient().submitIngestFile(file.bytes(), fileName, file.metadata());
        String documentId = job.getDocumentId();
        String message = ("Ingestion submitted for '%s' -> document %s. You can keep typing; "
                + "I'll report when it completes.")
                .formatted(path, documentId);
        pollIngestUntilDone(documentId, tokenSink);
        return new CommandResult(message, false);
    }

    private void pollIngestUntilDone(String documentId, Consumer<String> tokenSink) {
        Thread poller = new Thread(() -> {
            try {
                String label = "document " + documentId;
                while (true) {
                    Thread.sleep(POLL_MILLIS);
                    IngestStatusDTO status = clients.apiClient().ingestStatus(documentId);
                    IngestStatusDTO.StateEnum state = status.getState();
                    if (IngestStatusDTO.StateEnum.COMPLETED == state) {
                        tokenSink.accept("Ingestion of " + label + " complete: "
                                + status.getChunkCount() + " chunks.\n");
                        return;
                    }
                    if (IngestStatusDTO.StateEnum.FAILED == state) {
                        tokenSink.accept("Ingestion of " + label + " failed: " + status.getMessage() + "\n");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                tokenSink.accept("Ingestion of document " + documentId
                        + " could not be checked: " + e.getMessage() + "\n");
            }
        }, "rag-ingest-poll");
        poller.setDaemon(true);
        poller.start();
    }

    private static final long POLL_MILLIS = 2_000;

    private CommandResult addUrl(String url) {
        if (url.isEmpty()) return new CommandResult("Usage: add-url <url>", false);
        IngestResponse response = clients.apiClient().ingestUrl(url);
        return new CommandResult("Ingested %s -> document %s, %d chunks"
                .formatted(url, response.getDocumentId(), response.getChunkCount()), false);
    }

    private CommandResult ask(String question, Consumer<String> tokenSink) {
        if (question.isEmpty()) return new CommandResult("Usage: ask <question>", false);
        clients.chatGateway().ask(question, settings.topK(), tokenSink);
        return new CommandResult("", false);
    }

    private CommandResult history() {
        List<ConversationDTO> conversations = clients.memoryClient().conversations();
        if (conversations.isEmpty()) return new CommandResult("No conversations yet.", false);
        StringBuilder sb = new StringBuilder("Conversations:\n");
        for (ConversationDTO conversation : conversations) {
            int count = clients.memoryClient().messages(conversation.getId()).size();
            sb.append(" - ").append(conversation.getId())
                    .append(" (").append(conversation.getTitle() == null ? "" : conversation.getTitle())
                    .append(", ").append(count).append(" messages)\n");
        }
        return new CommandResult(sb.toString(), false);
    }

    public record RagClients(RagApiClient apiClient, ChatGateway chatGateway, MemoryClient memoryClient,
                             FileDocumentLoader fileLoader, ModuleHealthClient healthClient) {
    }

    public record Settings(long startTimeoutMs, int topK) {
    }
}