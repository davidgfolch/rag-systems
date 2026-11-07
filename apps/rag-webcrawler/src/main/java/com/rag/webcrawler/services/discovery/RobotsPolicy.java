package com.rag.webcrawler.services.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal, site-agnostic robots.txt reader: the {@code Sitemap:} directives
 * (the standard way to discover sitemaps) and the {@code Disallow:} rules for
 * the wildcard agent. Longest matching prefix wins, as the specification says.
 */
public final class RobotsPolicy {

    static final RobotsPolicy ALLOW_ALL = new RobotsPolicy(List.of(), List.of());

    private static final String SITEMAP_KEY = "sitemap";
    private static final String DISALLOW_KEY = "disallow";
    private static final String WILDCARD = "*";
    private static final String COMMENT = "#";
    private static final String SEPARATOR = ":";

    private final List<String> sitemaps;
    private final List<String> disallow;

    private RobotsPolicy(List<String> sitemaps, List<String> disallow) {
        this.sitemaps = sitemaps;
        this.disallow = disallow;
    }

    public static RobotsPolicy parse(String text) {
        if (text == null || text.isBlank()) return ALLOW_ALL;
        var sitemaps = new ArrayList<String>();
        var disallow = new ArrayList<String>();
        var wildcard = false;
        for (var rawLine : text.split("\\R")) {
            var line = rawLine.split(COMMENT, 2)[0].trim();
            var colon = line.indexOf(SEPARATOR);
            if (colon < 0) continue;
            var key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            var value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                wildcard = WILDCARD.equals(value);
            } else if (key.equals(SITEMAP_KEY)) {
                sitemaps.add(value);
            } else if (key.equals(DISALLOW_KEY) && wildcard) {
                disallow.add(value);
            }
        }
        return new RobotsPolicy(List.copyOf(sitemaps), List.copyOf(disallow));
    }

    public List<String> sitemaps() {
        return sitemaps;
    }

    public boolean allows(String path) {
        var match = "";
        for (var rule : disallow) {
            if (!rule.isEmpty() && path.startsWith(rule) && rule.length() > match.length()) match = rule;
        }
        return match.isEmpty();
    }
}
