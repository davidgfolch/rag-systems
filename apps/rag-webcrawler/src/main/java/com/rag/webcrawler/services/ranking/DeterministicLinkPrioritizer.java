package com.rag.webcrawler.services.ranking;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic fallback ranker: scores links by keyword overlap with the
 * question and by being close to the source domain. No LLM required.
 */
public class DeterministicLinkPrioritizer implements LinkPrioritizer {

    @Override
    public List<String> prioritize(List<String> links, String question) {
        String query = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return links.stream()
                .sorted(Comparator.comparing((String link) -> score(link, query))
                        .reversed().thenComparing(links::indexOf))
                .toList();
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