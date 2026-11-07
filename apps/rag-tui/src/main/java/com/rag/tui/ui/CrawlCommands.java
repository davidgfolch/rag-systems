package com.rag.tui.ui;

import com.rag.contract.model.CrawlPageDTO;
import com.rag.contract.model.CrawlStartResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    private static final String USAGE =
            "Usage: add-url <url> [--max-pages N] [--all] [--no-discover]";
    private static final String MAX_PAGES_FLAG = "--max-pages";
    private static final String ALL_FLAG = "--all";
    private static final String NO_DISCOVER_FLAG = "--no-discover";
    private static final int ALL_PAGES = Integer.MAX_VALUE;

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
        var options = CrawlOptions.parse(arg);
        if (!options.valid()) return USAGE;
        var url = options.url();
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
        var start = options.hasOptions()
                ? clients.apiClient().startCrawl(url, options.maxPages(), options.discover())
                : clients.apiClient().startCrawl(url);
        return run(start, tokenSink);
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
        int discovered = intOrZero(start.getDiscoveredPages());
        int ingested = 0;
        int skippedPages = 0;
        int skippedLinks = 0;
        boolean goOnAsked = false;
        try {
            var page = start.getPage();
            while (page != null) {
                var question = askQuestion(page);
                if (isCancel(question)) {
                    return cancelSummary(start.getSessionId(), ingested, skippedPages, skippedLinks, discovered);
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
                        return cancelSummary(start.getSessionId(), ingested, skippedPages, skippedLinks, discovered);
                    }
                }
            }
            cancel(start.getSessionId());
            return success(summary("Crawl finished", ingested, skippedPages, skippedLinks, discovered));
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

    private String cancelSummary(String sessionId, int ingested, int skippedPages, int skippedLinks,
                                 int discovered) {
        cancel(sessionId);
        return success(summary("Crawl cancelled", ingested, skippedPages, skippedLinks, discovered));
    }

    private void cancel(String sessionId) {
        try {
            clients.apiClient().cancelCrawl(sessionId);
        } catch (RuntimeException e) {
            log.warn("Could not cancel crawl session {}: {}", sessionId, e.getMessage());
        }
    }

    private static String summary(String prefix, int ingested, int skippedPages, int skippedLinks,
                                  int discovered) {
        return ("%s: %d page(s) submitted for async ingestion, %d page(s) skipped, %d link(s) skipped. "
                + "Discovery queued %d page(s). Ingestion continues in the background; "
                + "I'll report each page as it completes.")
                .formatted(prefix, ingested, skippedPages, skippedLinks, discovered);
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    record CrawlOptions(String url, Integer maxPages, boolean discover, boolean hasOptions, boolean valid) {

        static CrawlOptions parse(String arg) {
            var tokens = List.of(arg == null ? new String[0] : arg.trim().split("\\s+"));
            var url = "";
            Integer maxPages = null;
            boolean all = false;
            boolean discover = true;
            try {
                var iterator = tokens.iterator();
                while (iterator.hasNext()) {
                    var token = iterator.next();
                    if (MAX_PAGES_FLAG.equals(token)) {
                        maxPages = Integer.parseInt(iterator.next());
                    } else if (ALL_FLAG.equals(token)) {
                        all = true;
                    } else if (NO_DISCOVER_FLAG.equals(token)) {
                        discover = false;
                    } else if (!token.isEmpty()) {
                        url = url.isEmpty() ? token : url + " " + token;
                    }
                }
            } catch (RuntimeException _) {
                return new CrawlOptions("", null, true, false, false);
            }
            if (maxPages != null && maxPages < 1) return new CrawlOptions("", null, true, false, false);
            if (all) maxPages = ALL_PAGES;
            var hasOptions = all || maxPages != null || !discover;
            return new CrawlOptions(url, maxPages, discover, hasOptions, true);
        }
    }
}
