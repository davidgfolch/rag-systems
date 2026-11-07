package com.rag.webcrawler.services.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayDeque;

/**
 * Orders discovered URLs so a small page budget samples the whole tree: pages
 * are grouped by their parent directory, the shortest page in each directory is
 * visited first, and the directories are interleaved round-robin.
 */
final class StructuralOrder {

    private static final String SLASH = "/";

    private StructuralOrder() {}

    static List<String> order(List<String> urls) {
        var buckets = new LinkedHashMap<String, Deque<String>>();
        urls.stream().sorted(Comparator.comparingInt(String::length).thenComparing(url -> url))
                .forEach(url -> buckets.computeIfAbsent(directoryOf(url), _ -> new ArrayDeque<>()).add(url));
        var ordered = new ArrayList<String>(urls.size());
        var added = true;
        while (added) {
            added = false;
            for (var bucket : buckets.values()) {
                var next = bucket.poll();
                if (next != null) {
                    ordered.add(next);
                    added = true;
                }
            }
        }
        return ordered;
    }

    private static String directoryOf(String url) {
        try {
            var path = URI.create(url).getPath();
            if (path == null) return "";
            var lastSlash = path.lastIndexOf(SLASH);
            return lastSlash < 0 ? "" : path.substring(0, lastSlash + 1);
        } catch (IllegalArgumentException _) {
            return "";
        }
    }
}
