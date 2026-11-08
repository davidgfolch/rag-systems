package com.rag.tui.ui;

import com.rag.contract.model.CrawlStartResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.function.Consumer;

import static com.rag.tui.ui.CommandDispatcher.orEmpty;
import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Drives the interactive breadth-first {@code add-url} crawl. The TUI only
 * prompts and calls the active rag-module; the module owns the frontier and
 * submits each page for asynchronous ingestion. In interactive mode the
 * link-ranking question is asked once and reused, and "Go on?" is only asked
 * when the user has not already set a page cap. {@code --all} runs without
 * prompts.
 */
public class CrawlCommands {

    static final String CANCEL_COMMAND = "add-url-cancel";
    private static final String CANCEL_ALIAS = "cancel";
    private static final String QUESTION_PROMPT =
            "Question to rank links for this crawl (blank = none, '%s' to stop): ";
    private static final String GO_ON_PROMPT =
            "Initial pages submitted. Go on and crawl more pages? (y/N) ";
    private static final String NO_ACTIVE_CRAWL = "No URL crawl is active.";
    private static final String USAGE =
            "Usage: add-url <url> [--max-pages N] [--all] [--override-all] [--no-discover]";

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
        var question = options.interactive() ? askQuestion() : "";
        if (isCancel(question)) return success("Crawl cancelled.");
        var guard = guard(options);
        var start = options.hasOptions()
                ? clients.apiClient().startCrawl(url, options.maxPages(), options.discover())
                : clients.apiClient().startCrawl(url);
        return run(start, options, question, guard, tokenSink);
    }

    /** Cancels the crawl currently being driven, if any. */
    public String cancelActive() {
        var sessionId = activeSessionId;
        if (sessionId == null) return NO_ACTIVE_CRAWL;
        cancel(sessionId);
        activeSessionId = null;
        return success("URL crawl cancelled.");
    }

    private CrawlDuplicateGuard guard(CrawlOptions options) {
        var index = options.all() && !options.overrideAll()
                ? Map.<String, DuplicateFinder.Match>of()
                : duplicates.sourceIndex();
        return new CrawlDuplicateGuard(index, prompter, clients.apiClient(),
                options.overrideAll(), options.all());
    }

    private String run(CrawlStartResponse start, CrawlOptions options, String question,
                       CrawlDuplicateGuard guard, Consumer<String> tokenSink) {
        activeSessionId = start.getSessionId();
        var batch = new IngestBatch("Crawl ingestion", tokenSink);
        int discovered = intOrZero(start.getDiscoveredPages());
        int ingested = 0;
        int skippedPages = 0;
        int skippedLinks = 0;
        boolean goOnAsked = false;
        try {
            var page = start.getPage();
            while (page != null) {
                var step = clients.apiClient().stepCrawl(start.getSessionId(), question,
                        guard.overrideFor(page.getUrl()));
                if (Boolean.TRUE.equals(step.getIngested()) && step.getDocumentId() != null) {
                    poller.start(step.getDocumentId(), null, "page " + step.getPageUrl(), tokenSink, batch);
                    ingested++;
                } else {
                    skippedPages++;
                }
                skippedLinks += intOrZero(step.getSkippedLinks());
                page = step.getNextPage();
                if (page != null && intOrZero(step.getInitialRemaining()) == 0 && !goOnAsked
                        && options.interactive() && !options.hasExplicitLimit()) {
                    goOnAsked = true;
                    if (!prompter.confirm(GO_ON_PROMPT)) {
                        return cancelSummary(start.getSessionId(), ingested, skippedPages, skippedLinks, discovered);
                    }
                }
            }
            cancel(start.getSessionId());
            return success(summary("Crawl finished", ingested, skippedPages, skippedLinks, discovered));
        } catch (RestClientException e) {
            log.warn("Crawl {} interrupted: {}", start.getSessionId(), e.getMessage());
            cancel(start.getSessionId());
            return error("Crawl interrupted: " + e.getMessage() + "\n"
                    + summary("Crawl partial", ingested, skippedPages, skippedLinks, discovered));
        } finally {
            batch.close();
            activeSessionId = null;
        }
    }

    private String askQuestion() {
        var answer = prompter.prompt(QUESTION_PROMPT.formatted(CANCEL_COMMAND));
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
                + "Discovery queued %d page(s). I'll report each page and print a summary when all async jobs finish.")
                .formatted(prefix, ingested, skippedPages, skippedLinks, discovered);
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
