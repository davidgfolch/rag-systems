package com.rag.tui.ui;

import java.util.List;

/**
 * Parsed {@code add-url} arguments: the URL plus the page cap, discovery and
 * override flags. An explicit cap (from {@code --max-pages} or {@code --all})
 * means the user already decided how far to crawl, so no "Go on?" prompt is
 * needed. {@code --all} also makes the whole crawl non-interactive.
 */
record CrawlOptions(String url, Integer maxPages, boolean discover, boolean overrideAll,
                    boolean all, boolean valid) {

    private static final String MAX_PAGES_FLAG = "--max-pages";
    private static final String ALL_FLAG = "--all";
    private static final String NO_DISCOVER_FLAG = "--no-discover";
    private static final String OVERRIDE_ALL_FLAG = "--override-all";
    private static final int ALL_PAGES = Integer.MAX_VALUE;

    static CrawlOptions parse(String arg) {
        var tokens = List.of(arg == null ? new String[0] : arg.trim().split("\\s+"));
        var url = "";
        Integer maxPages = null;
        boolean all = false;
        boolean overrideAll = false;
        boolean discover = true;
        try {
            var iterator = tokens.iterator();
            while (iterator.hasNext()) {
                var token = iterator.next();
                if (MAX_PAGES_FLAG.equals(token)) {
                    maxPages = Integer.parseInt(iterator.next());
                } else if (ALL_FLAG.equals(token)) {
                    all = true;
                } else if (OVERRIDE_ALL_FLAG.equals(token)) {
                    overrideAll = true;
                } else if (NO_DISCOVER_FLAG.equals(token)) {
                    discover = false;
                } else if (!token.isEmpty()) {
                    url = url.isEmpty() ? token : url + " " + token;
                }
            }
        } catch (RuntimeException _) {
            return invalid();
        }
        if (maxPages != null && maxPages < 1) return invalid();
        if (all) maxPages = ALL_PAGES;
        return new CrawlOptions(url, maxPages, discover, overrideAll, all, true);
    }

    /** Whether the user asked for crawl options that must be sent to the module. */
    boolean hasOptions() {
        return all || maxPages != null || !discover;
    }

    /** An explicit cap (including {@code --all}) removes the "Go on?" prompt. */
    boolean hasExplicitLimit() {
        return maxPages != null;
    }

    /** {@code --all} crawls without any prompt. */
    boolean interactive() {
        return !all;
    }

    private static CrawlOptions invalid() {
        return new CrawlOptions("", null, true, false, false, false);
    }
}
