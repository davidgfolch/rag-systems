package com.rag.advanced.retrieval;

import com.rag.common.core.domain.Chunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BM25-style lexical scorer evaluated over a candidate set (no external corpus).
 * Tokens are lower-cased words; term frequency is smoothed and rare terms are
 * weighted by inverse document frequency computed against the candidates.
 */
public final class LexicalScorer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}']+");
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;
    private static final double DELTA = 0.5d;

    /**
     * Returns one BM25 score per candidate, aligned with the input order.
     */
    public double[] score(String query, List<Chunk> candidates) {
        var queryTerms = unique(tokenize(query));
        if (queryTerms.isEmpty() || candidates.isEmpty()) {
            return new double[candidates.size()];
        }
        var docs = candidates.stream()
                .map(chunk -> counts(tokenize(chunk.getContent())))
                .map(termFreqs -> new DocStats(termFreqs, totalTokens(termFreqs)))
                .toList();
        var totalTokens = docs.stream().mapToInt(DocStats::length).sum();
        var docFrequency = docs.stream()
                .flatMap(doc -> doc.termFreqs().keySet().stream())
                .collect(Collectors.toMap(term -> term, _ -> 1, Integer::sum));
        var avgDocLength = totalTokens / (double) docs.size();
        return docs.stream()
                .mapToDouble(doc -> bm25(queryTerms, doc.termFreqs(), doc.length(),
                        avgDocLength, docFrequency, docs.size()))
                .toArray();
    }

    private static double bm25(Set<String> queryTerms, Map<String, Integer> termFreq, int docLength,
                               double avgDocLength, Map<String, Integer> docFrequency, int docCount) {
        double score = 0.0d;
        for (var term : queryTerms) {
            int freq = termFreq.getOrDefault(term, 0);
            if (freq == 0) {
                continue;
            }
            int df = docFrequency.getOrDefault(term, 0);
            double idf = Math.log(1.0d + ((docCount - df + 0.5d) / (df + 0.5d)));
            double normalization = freq * (K1 + 1.0d) / (freq + K1 * (1.0d - B + B * (docLength / avgDocLength)));
            score += idf * (normalization + DELTA);
        }
        return score;
    }

    private static List<String> tokenize(String text) {
        return TOKEN_PATTERN.matcher(text == null ? "" : text.toLowerCase())
                .results()
                .map(MatchResult::group)
                .toList();
    }

    private static Set<String> unique(List<String> tokens) {
        return Set.copyOf(tokens);
    }

    private static Map<String, Integer> counts(List<String> tokens) {
        var counts = new HashMap<String, Integer>();
        tokens.forEach(token -> counts.merge(token, 1, Integer::sum));
        return counts;
    }

    private static int totalTokens(Map<String, Integer> termFreqs) {
        return termFreqs.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** One candidate's term frequencies and token length. */
    private record DocStats(Map<String, Integer> termFreqs, int length) {
    }
}
