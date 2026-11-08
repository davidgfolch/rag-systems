package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Collapses identical chunk text returned by a similarity search. Near-duplicate
 * documents (crawled page versions, mirrored paths) produce chunks with the same
 * content, which would otherwise fill every {@code topK} slot with one repeated
 * snippet. Stores over-fetch so distinct results can still fill the requested
 * window.
 */
public final class ContentDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(ContentDeduplicator.class);

    static final int OVERFETCH_FACTOR = 4;
    static final int MAX_FETCH_SIZE = 256;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ContentDeduplicator() {
    }

    /** Candidate count a store must request so duplicates cannot starve {@code topK} distinct hits. */
    public static int fetchSize(int topK) {
        if (topK <= 0) {
            return 0;
        }
        if (topK > MAX_FETCH_SIZE) {
            return topK;
        }
        return Math.clamp((long) topK * OVERFETCH_FACTOR, topK, MAX_FETCH_SIZE);
    }

    /** Keeps the first (highest-ranked) occurrence of every distinct content, up to {@code topK}. */
    public static List<Chunk> distinct(List<Chunk> hits, int topK) {
        if (hits == null || hits.isEmpty() || topK <= 0) {
            return List.of();
        }
        var seen = new HashSet<String>();
        var distinct = new ArrayList<Chunk>(Math.min(hits.size(), topK));
        for (var chunk : hits) {
            if (distinct.size() >= topK) {
                break;
            }
            if (seen.add(normalize(chunk.getContent()))) {
                distinct.add(chunk);
            }
        }
        if (distinct.size() < hits.size()) {
            log.debug("Collapsed {} duplicate hit(s) of {} candidates", hits.size() - distinct.size(), hits.size());
        }
        return distinct;
    }

    private static String normalize(String content) {
        return content == null ? "" : WHITESPACE.matcher(content.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
