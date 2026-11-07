package com.rag.webcrawler.services.fetching;

import org.jsoup.nodes.Document;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Collects links that live in navigation-like containers. Repeated across
 * pages, these are the site's own canonical tree, which is a better crawl
 * frontier than every anchor in DOM order.
 */
final class NavLinkExtractor {

    private static final String SELECTOR = "nav a[href], [role=navigation] a[href], aside a[href],"
            + " .toc a[href], .sidebar a[href], .sidebar-links a[href]";

    List<String> extract(Document document) {
        var links = new LinkedHashSet<String>();
        document.select(SELECTOR).forEach(anchor -> {
            var raw = anchor.attr("href").trim();
            if (raw.isEmpty() || raw.startsWith("#")) return;
            var href = anchor.absUrl("href");
            if (!href.isBlank()) links.add(href);
        });
        return List.copyOf(links);
    }
}
