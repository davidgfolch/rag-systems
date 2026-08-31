package com.rag.tui.ui;

import com.rag.common.domain.Document;
import com.rag.tui.fetching.WebPage;
import com.rag.tui.fetching.WebPageFetcher;
import com.rag.tui.services.ChatService;
import com.rag.tui.services.FileDocumentLoader;
import com.rag.tui.services.IngestionService;

import java.util.Map;
import java.util.UUID;

/**
 * Parses terminal command lines and routes them to the ingestion/chat services.
 * Kept free of I/O so it is purely unit-testable.
 */
public class CommandDispatcher {

    private static final String USAGE = """
            Available commands:
              help                    show this help
              add-file <path>         ingest a local document (PDF/DOCX/TXT/HTML)
              add-url <url>           ingest a web page
              ask <question>          ask a question against ingested documents
              quit                    exit the terminal
            """;

    private final IngestionService ingestionService;
    private final ChatService chatService;
    private final WebPageFetcher webPageFetcher;
    private final FileDocumentLoader fileLoader;
    private final int topK;

    public CommandDispatcher(IngestionService ingestionService, ChatService chatService,
                             WebPageFetcher webPageFetcher, FileDocumentLoader fileLoader) {
        this(ingestionService, chatService, webPageFetcher, fileLoader, 4);
    }

    public CommandDispatcher(IngestionService ingestionService, ChatService chatService,
                             WebPageFetcher webPageFetcher, FileDocumentLoader fileLoader, int topK) {
        this.ingestionService = ingestionService;
        this.chatService = chatService;
        this.webPageFetcher = webPageFetcher;
        this.fileLoader = fileLoader;
        this.topK = topK;
    }

    public CommandResult handle(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new CommandResult(USAGE, false);

        String lower = trimmed.toLowerCase();
        if (lower.equals("quit") || lower.equals("exit")) return new CommandResult("Bye.", true);
        if (lower.equals("help")) return new CommandResult(USAGE, false);

        String[] parts = trimmed.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1].trim() : "";
        return switch (parts[0].toLowerCase()) {
            case "add-file" -> addFile(arg);
            case "add-url" -> addUrl(arg);
            case "ask" -> ask(arg);
            default -> new CommandResult("Unknown command. Type 'help' for usage.\n\n" + USAGE, false);
        };
    }

    private CommandResult addFile(String path) {
        if (path.isEmpty()) return new CommandResult("Usage: add-file <path>", false);
        IngestionService.IngestionResult result = ingestionService.ingest(fileLoader.load(path));
        return new CommandResult(
                "Ingested document %s -> %d chunks".formatted(result.documentId(), result.chunkCount()), false);
    }

    private CommandResult addUrl(String url) {
        if (url.isEmpty()) return new CommandResult("Usage: add-url <url>", false);
        WebPage page = webPageFetcher.fetch(url);
        Document doc = new Document("web-" + UUID.randomUUID(), page.text(),
                Map.of("sourceType", "web", "source", page.url()));
        IngestionService.IngestionResult result = ingestionService.ingest(doc);
        return new CommandResult(
                "Ingested web page %s -> %d chunks".formatted(page.url(), result.chunkCount()), false);
    }

    private CommandResult ask(String question) {
        if (question.isEmpty()) return new CommandResult("Usage: ask <question>", false);
        ChatService.ChatResult result = chatService.ask(question, topK);
        StringBuilder sb = new StringBuilder("Answer:\n").append(result.answer());
        if (!result.sources().isEmpty()) {
            sb.append("\n\nSources:\n");
            result.sources().forEach(c -> sb.append(" - ").append(c.getContent()).append('\n'));
        }
        return new CommandResult(sb.toString(), false);
    }
}
