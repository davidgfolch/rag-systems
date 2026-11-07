package com.rag.webcrawler.services.discovery;

import com.rag.webcrawler.services.fetching.WebPageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Union of navigation links and sitemap entries, filtered to the start URL's
 * scope, collapsed by URL family, then arranged in structural order. This is
 * the site-agnostic replacement for following a page's links in DOM order.
 */
public class UrlDiscoveryService implements UrlDiscovery {

    private static final Logger log = LoggerFactory.getLogger(UrlDiscoveryService.class);

    public static final int DEFAULT_MAX_URLS = 2000;

    private final WebPageFetcher fetcher;
    private final SitemapUrlDiscovery sitemaps;
    private final int maxUrls;

    public UrlDiscoveryService(WebPageFetcher fetcher, SitemapUrlDiscovery sitemaps, int maxUrls) {
        this.fetcher = fetcher;
        this.sitemaps = sitemaps;
        this.maxUrls = maxUrls;
    }

    @Override
    public List<String> discover(String startUrl) {
        return discover(startUrl, maxUrls);
    }

    public List<String> discover(String startUrl, int limit) {
        var scope = UrlScope.of(startUrl);
        var candidates = new LinkedHashSet<String>();
        addNavigation(candidates, scope, startUrl);
        candidates.addAll(sitemaps.discover(startUrl));
        var startKey = scope.canonical(startUrl);
        var urls = candidates.stream()
                .filter(scope::accepts)
                .map(scope::canonical)
                .filter(Objects::nonNull)
                .filter(url -> !url.equals(startKey))
                .distinct()
                .toList();
        var selected = new UrlFamilySelector().select(urls, startUrl);
        var ordered = StructuralOrder.order(selected);
        var limited = ordered.stream().limit(Math.max(0, limit)).toList();
        log.info("Discovered {} crawlable URLs for {} (cap {})", limited.size(), startUrl, limit);
        return limited;
    }

    private void addNavigation(LinkedHashSet<String> candidates, UrlScope scope, String startUrl) {
        try {
            var page = fetcher.fetch(startUrl);
            var links = page.getNavLinks();
            if (links == null || links.isEmpty()) links = page.getLinks();
            if (links == null) return;
            links.stream().filter(scope::accepts).forEach(candidates::add);
        } catch (RuntimeException e) {
            log.warn("Navigation discovery failed for {}: {}", startUrl, e.getMessage());
        }
    }
}
