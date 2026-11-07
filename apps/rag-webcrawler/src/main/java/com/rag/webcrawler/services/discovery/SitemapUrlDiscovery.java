package com.rag.webcrawler.services.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Standards-based discovery: reads {@code Sitemap:} directives from robots.txt,
 * probes the conventional sitemap locations, follows sitemap indexes and gzip
 * files, then keeps only in-scope HTML pages. Works for any site; no site
 * specific paths or version names are hardcoded.
 */
public class SitemapUrlDiscovery {

    private static final Logger log = LoggerFactory.getLogger(SitemapUrlDiscovery.class);

    static final int MAX_SITEMAPS = 25;
    static final int MAX_DEPTH = 2;
    private static final String ROBOTS_PATH = "/robots.txt";
    private static final String SITEMAP = "sitemap.xml";
    private static final String SITEMAP_INDEX = "sitemap_index.xml";
    private static final String SLASH = "/";
    private static final String LOC = "loc";
    private static final String SITEMAP_INDEX_TAG = "sitemapindex";
    private static final String DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    private final UrlContentLoader loader;
    private final int maxUrls;

    public SitemapUrlDiscovery(UrlContentLoader loader, int maxUrls) {
        this.loader = loader;
        this.maxUrls = maxUrls;
    }

    public List<String> discover(String startUrl) {
        var scope = UrlScope.of(startUrl);
        var robots = robots(scope);
        var found = new LinkedHashSet<String>();
        var visited = new LinkedHashSet<String>();
        for (var candidate : candidates(scope, robots)) {
            if (found.size() >= maxUrls || visited.size() >= MAX_SITEMAPS) break;
            collect(candidate, found, visited, 0);
        }
        var urls = found.stream()
                .filter(scope::accepts)
                .filter(url -> robots.allows(pathOf(url)))
                .map(scope::canonical)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        log.info("Sitemap discovery found {} in-scope URLs for {}", urls.size(), startUrl);
        return new UrlFamilySelector().select(urls, startUrl);
    }

    private void collect(String sitemapUrl, Set<String> found, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH || found.size() >= maxUrls || !visited.add(sitemapUrl)) return;
        var body = loader.load(sitemapUrl);
        if (body.isEmpty()) return;
        var document = parse(body.get());
        if (document == null) return;
        var index = isIndex(document);
        var locs = document.getElementsByTagName(LOC);
        for (int i = 0; i < locs.getLength() && found.size() < maxUrls; i++) {
            var value = locs.item(i).getTextContent().trim();
            if (value.isEmpty()) continue;
            if (index) collect(value, found, visited, depth + 1);
            else found.add(value);
        }
    }

    private RobotsPolicy robots(UrlScope scope) {
        return loader.load(scope.origin() + ROBOTS_PATH)
                .map(bytes -> RobotsPolicy.parse(new String(bytes, StandardCharsets.UTF_8)))
                .orElse(RobotsPolicy.ALLOW_ALL);
    }

    private static Set<String> candidates(UrlScope scope, RobotsPolicy robots) {
        var candidates = new LinkedHashSet<>(robots.sitemaps());
        candidates.add(scope.origin() + scope.baseDir() + SITEMAP);
        candidates.add(scope.origin() + scope.baseDir() + SITEMAP_INDEX);
        candidates.add(scope.origin() + SLASH + SITEMAP);
        candidates.add(scope.origin() + SLASH + SITEMAP_INDEX);
        return candidates;
    }

    private static boolean isIndex(Document document) {
        var root = document.getDocumentElement();
        return root != null && root.getTagName().toLowerCase().endsWith(SITEMAP_INDEX_TAG);
    }

    private static Document parse(byte[] body) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(DOCTYPE_FEATURE, true);
            factory.setFeature(EXTERNAL_ENTITIES, false);
            factory.setFeature(PARAMETER_ENTITIES, false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("Ignoring malformed sitemap: {}", e.getMessage());
            return null;
        }
    }

    private static String pathOf(String url) {
        try {
            var path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? SLASH : path;
        } catch (IllegalArgumentException _) {
            return SLASH;
        }
    }
}
