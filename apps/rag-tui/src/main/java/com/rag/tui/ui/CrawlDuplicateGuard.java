package com.rag.tui.ui;

import com.rag.common.core.util.WebUrls;
import com.rag.tui.client.RagApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides, page by page, whether an already-ingested page should be skipped or
 * overridden. One bulk decision (override all / skip all) applies to the rest
 * of the crawl. Overriding deletes the old document before the module is asked
 * to ingest its replacement, so no stale chunks remain.
 */
public class CrawlDuplicateGuard {

    private static final Logger log = LoggerFactory.getLogger(CrawlDuplicateGuard.class);
    private static final String PROMPT =
            "Already ingested as document %s on %s. [Enter=override, Esc=ignore, y=override rest, n=ignore rest] ";

    private final Map<String, DuplicateFinder.Match> sources;
    private final Prompter prompter;
    private final RagApiClient apiClient;
    private final boolean overrideAll;
    private final boolean skipAll;
    private boolean overrideRest;
    private boolean skipRest;

    public CrawlDuplicateGuard(Map<String, DuplicateFinder.Match> sources, Prompter prompter,
                               RagApiClient apiClient, boolean overrideAll, boolean skipAll) {
        this.sources = new LinkedHashMap<>(sources);
        this.prompter = prompter;
        this.apiClient = apiClient;
        this.overrideAll = overrideAll;
        this.skipAll = skipAll;
    }

    /**
     * Whether the page must override an already-ingested document. Returns false
     * both when there is no duplicate and when the page should simply be skipped.
     */
    public boolean overrideFor(String url) {
        var key = WebUrls.key(url);
        if (key == null) return false;
        var match = sources.get(key);
        if (match == null) return false;
        if (overrideAll || overrideRest) return delete(match, key);
        if (skipAll || skipRest) return false;
        return switch (decision(match)) {
            case OVERRIDE -> delete(match, key);
            case OVERRIDE_ALL -> {
                overrideRest = true;
                yield delete(match, key);
            }
            case SKIP_ALL -> {
                skipRest = true;
                yield false;
            }
            case SKIP -> false;
        };
    }

    private Prompter.OverrideDecision decision(DuplicateFinder.Match match) {
        var answer = prompter.overrideDecision(PROMPT.formatted(match.documentId(), match.moduleName()));
        return answer == null ? Prompter.OverrideDecision.SKIP : answer;
    }

    private boolean delete(DuplicateFinder.Match match, String key) {
        apiClient.deleteDocument(match.baseUrl(), match.documentId());
        sources.remove(key);
        log.info("Overriding duplicate document {} for {}", match.documentId(), key);
        return true;
    }
}
