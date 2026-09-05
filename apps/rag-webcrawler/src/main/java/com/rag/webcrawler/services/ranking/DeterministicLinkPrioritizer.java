package com.rag.webcrawler.services.ranking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic fallback ranker: scores links by keyword overlap with the
 * question and by being close to the source domain. No LLM required.
 */
public class DeterministicLinkPrioritizer implements LinkPrioritizer {

    private static final Logger log = LoggerFactory.getLogger(DeterministicLinkPrioritizer.class);

    @Override
    public List<String> prioritize(List<String> links, String question) {
        String query = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<String> ranked = links.stream()
                .sorted(Comparator.comparing((String link) -> score(link, query))
                        .reversed().thenComparing(links::indexOf))
                .toList();
        log.debug("Deterministic prioritization of {} links", ranked.size());
        return ranked;
    }

    private int score(String link, String query) {
        String lower = link.toLowerCase(Locale.ROOT);
        int score = lower.startsWith("http") ? 0 : 1;
        if (query.isBlank()) return score;
        for (String word : query.split("\\s+")) {
            if (word.length() > 3 && lower.contains(word)) score += 2;
        }
        return score;
    }
}