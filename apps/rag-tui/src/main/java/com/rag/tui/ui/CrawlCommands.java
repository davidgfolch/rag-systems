package com.rag.tui.ui;

import com.rag.contract.model.CrawlPageDTO;
import com.rag.contract.model.CrawlStartResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static com.rag.tui.ui.CommandDispatcher.orEmpty;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Drives the interactive breadth-first {@code add-url} crawl. The TUI only
 * prompts and calls the active rag-module; the module owns the frontier and
 * submits each page for asynchronous ingestion. Every page, including the root,
 * is preceded by a question that ranks that page's outbound links, and after the
 * initial set one single "Go on?" prompt decides whether to keep crawling.
 */
public class CrawlCommands {

    static final String CANCEL_COMMAND = "add-url-cancel";
    private static final String CANCEL_ALIAS = "cancel";
    private static final String QUESTION_PROMPT =
            "Question for \"%s\" (blank = none, '%s' to stop): ";
    private static final String GO_ON_PROMPT =
            "Initial pages submitted. Go on and crawl more pages? (y/N) ";
    private static final String NO_ACTIVE_CRAWL = "No URL crawl is active.";
    private static final String USAGE = "Usage: add-url <url>";

    private static final Logger log = LoggerFactory.getLogger(CrawlCommands.class);

    private final CommandDispatcher.RagClients clients;
    private final Prompter prompter;
    private final DuplicateFinder duplicates;
    private final AsyncIngestPoller poller;
    private volatile String activeSessionId;

    public CrawlCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                         DuplicateFinder duplicates, Tracer tracer) {
        this(clients, prompter, duplicates, new AsyncIngestPoller(clients, tracer));
    }

    CrawlCommands(CommandDispatcher.RagClients clients, Prompter prompter,
                  DuplicateFinder duplicates, AsyncIngestPoller poller) {
        this.clients = clients;
        this.prompter = prompter;
        this.duplicates = duplicates;
        this.poller = poller;
    }

    public String addUrl(String arg, Consumer<String> tokenSink) {
        var url = arg == null ? "" : arg.trim();
        if (url.isEmpty()) {
            url = orEmpty(prompter.prompt("URL: "));
            if (url.isEmpty()) return USAGE;
        }
        var duplicate = duplicates.findBySource(url);
        if (duplicate.isPresent()) {
            var match = duplicate.get();
            if (!prompter.confirm("Duplicate web page %s already ingested as document %s on %s. Override? (y/N) "
                    .formatted(url, match.documentId(), match.moduleName()))) {
                return "Skipped - already ingested as document " + match.documentId()
                        + " on " + match.moduleName() + ".";
            }
            clients.apiClient().deleteDocument(match.baseUrl(), match.documentId());
            log.info("Overriding duplicate document {} for {}", match.documentId(), url);
        }
        return run(clients.apiClient().startCrawl(url), tokenSink);
    }

    /** Cancels the crawl currently being driven, if any. */
    public String cancelActive() {
        var sessionId = activeSessionId;
        if (sessionId == null) return NO_ACTIVE_CRAWL;
        cancel(sessionId);
        activeSessionId = null;
        return success("URL crawl cancelled.");
    }

    private String run(CrawlStartResponse start, Consumer<String> tokenSink) {
        activeSessionId = start.getSessionId();
        int ingested = 0;
        int skippedPages = 0;
        int skippedLinks = 0;
        boolean goOnAsked = false;
        try {
            var page = start.getPage();
            while (page != null) {
                var question = askQuestion(page);
                if (isCancel(question)) {
                    return cancelSummary(start.getSessionId(), ingested, skippedPages, skippedLinks);
                }
                var step = clients.apiClient().stepCrawl(start.getSessionId(), question);
                if (Boolean.TRUE.equals(step.getIngested()) && step.getDocumentId() != null) {
                    poller.start(step.getDocumentId(), null, "page " + step.getPageUrl(), tokenSink);
                    ingested++;
                } else {
                    skippedPages++;
                }
                skippedLinks += intOrZero(step.getSkippedLinks());
                page = step.getNextPage();
                if (page != null && intOrZero(step.getInitialRemaining()) == 0 && !goOnAsked) {
                    goOnAsked = true;
                    if (!prompter.confirm(GO_ON_PROMPT)) {
                        return cancelSummary(start.getSessionId(), ingested, skippedPages, skippedLinks);
                    }
                }
            }
            cancel(start.getSessionId());
            return success(summary("Crawl finished", ingested, skippedPages, skippedLinks));
        } finally {
            activeSessionId = null;
        }
    }

    private String askQuestion(CrawlPageDTO page) {
        var title = page.getTitle().isBlank() ? page.getUrl() : page.getTitle();
        var answer = prompter.prompt(QUESTION_PROMPT.formatted(title, CANCEL_COMMAND));
        return answer == null ? CANCEL_COMMAND : answer.trim();
    }

    private static boolean isCancel(String answer) {
        return CANCEL_COMMAND.equalsIgnoreCase(answer) || CANCEL_ALIAS.equalsIgnoreCase(answer);
    }

    private String cancelSummary(String sessionId, int ingested, int skippedPages, int skippedLinks) {
        cancel(sessionId);
        return success(summary("Crawl cancelled", ingested, skippedPages, skippedLinks));
    }

    private void cancel(String sessionId) {
        try {
            clients.apiClient().cancelCrawl(sessionId);
        } catch (RuntimeException e) {
            log.warn("Could not cancel crawl session {}: {}", sessionId, e.getMessage());
        }
    }

    private static String summary(String prefix, int ingested, int skippedPages, int skippedLinks) {
        return ("%s: %d page(s) submitted for async ingestion, %d page(s) skipped, %d link(s) skipped. "
                + "Ingestion continues in the background; I'll report each page as it completes.")
                .formatted(prefix, ingested, skippedPages, skippedLinks);
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
