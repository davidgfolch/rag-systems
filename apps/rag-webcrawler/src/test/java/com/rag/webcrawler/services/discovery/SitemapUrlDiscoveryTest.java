package com.rag.webcrawler.services.discovery;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SitemapUrlDiscoveryTest {

    private static final String START = "https://example.com/docs/";
    private static final String ROBOTS = "https://example.com/robots.txt";

    private final Map<String, String> pages = new HashMap<>();
    private final UrlContentLoader loader = url -> Optional.ofNullable(pages.get(url))
            .map(body -> body.getBytes(StandardCharsets.UTF_8));

    @Test
    void readsTheSitemapAdvertisedInRobotsAndFiltersScope() {
        pages.put(ROBOTS, "User-agent: *\nSitemap: https://example.com/sitemap.xml");
        pages.put("https://example.com/sitemap.xml", urlset(
                "https://example.com/docs/a",
                "https://example.com/docs/b/",
                "https://example.com/other/c",
                "https://example.com/docs/logo.png"));

        var urls = new SitemapUrlDiscovery(loader, 100).discover(START);

        assertThat(urls).containsExactlyInAnyOrder(
                "https://example.com/docs/a", "https://example.com/docs/b");
    }

    @Test
    void followsSitemapIndexes() {
        pages.put(ROBOTS, "Sitemap: https://example.com/sitemap_index.xml");
        pages.put("https://example.com/sitemap_index.xml", sitemapIndex(
                "https://example.com/child.xml"));
        pages.put("https://example.com/child.xml", urlset("https://example.com/docs/deep"));

        var urls = new SitemapUrlDiscovery(loader, 100).discover(START);

        assertThat(urls).containsExactly("https://example.com/docs/deep");
    }

    @Test
    void usesConventionalSitemapLocationsWhenRobotsHasNoDirective() {
        pages.put("https://example.com/docs/sitemap.xml", urlset("https://example.com/docs/a"));

        var urls = new SitemapUrlDiscovery(loader, 100).discover(START);

        assertThat(urls).containsExactly("https://example.com/docs/a");
    }

    @Test
    void appliesRobotsDisallowRules() {
        pages.put(ROBOTS, "User-agent: *\nDisallow: /docs/private\nSitemap: https://example.com/sitemap.xml");
        pages.put("https://example.com/sitemap.xml", urlset(
                "https://example.com/docs/private/x", "https://example.com/docs/public"));

        var urls = new SitemapUrlDiscovery(loader, 100).discover(START);

        assertThat(urls).containsExactly("https://example.com/docs/public");
    }

    @Test
    void collapsesVersionedCopies() {
        pages.put("https://example.com/docs/sitemap.xml", urlset(
                "https://example.com/docs/3.0/a", "https://example.com/docs/3.1/a"));

        var urls = new SitemapUrlDiscovery(loader, 100).discover(START);

        assertThat(urls).singleElement().asString().contains("/docs/3.0/a").doesNotContain("3.1");
    }

    @Test
    void ignoresMalformedSitemaps() {
        pages.put("https://example.com/docs/sitemap.xml", "not xml at all");

        assertThat(new SitemapUrlDiscovery(loader, 100).discover(START)).isEmpty();
    }

    @Test
    void respectsTheUrlCap() {
        pages.put("https://example.com/docs/sitemap.xml", urlset(
                "https://example.com/docs/a", "https://example.com/docs/b", "https://example.com/docs/c"));

        assertThat(new SitemapUrlDiscovery(loader, 2).discover(START)).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void stopsProbingOnceTheDiscoveryDeadlineExpires() {
        var loaded = new ArrayList<String>();
        var countingLoader = (UrlContentLoader) url -> {
            loaded.add(url);
            return Optional.of(urlset("https://example.com/docs/a", "https://example.com/docs/b")
                    .getBytes(StandardCharsets.UTF_8));
        };
        var urls = new SitemapUrlDiscovery(countingLoader, 100, Duration.ofNanos(1)).discover(START);
        assertThat(urls).isEmpty();
        assertThat(loaded).containsExactly(ROBOTS);
    }

    private static String urlset(String... locations) {
        var body = new StringBuilder("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (var location : locations) body.append("<url><loc>").append(location).append("</loc></url>");
        return body.append("</urlset>").toString();
    }

    private static String sitemapIndex(String... locations) {
        var body = new StringBuilder("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (var location : locations) body.append("<sitemap><loc>").append(location).append("</loc></sitemap>");
        return body.append("</sitemapindex>").toString();
    }
}
