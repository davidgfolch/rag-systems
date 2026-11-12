package com.rag.advanced.services;

import com.rag.advanced.retrieval.LexicalScorer;
import com.rag.advanced.retrieval.MetadataFilter;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.DocumentStorePort;
import com.rag.common.core.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Two-stage retrieval: widen with a pure vector pass, then re-rank the
 * candidates by fusing the vector order with a BM25-style lexical pass using
 * reciprocal rank fusion. Optional metadata filtering narrows candidates before
 * the re-rank. Document listing and deletion are delegated to the vector store.
 */
public class AdvancedRetrievalService implements DocumentStorePort {

    private static final Logger log = LoggerFactory.getLogger(AdvancedRetrievalService.class);
    private static final int CANDIDATE_MULTIPLIER = 4;
    private static final double RRF_K = 60.0d;

    private final VectorStorePort vectorStore;
    private final LexicalScorer lexicalScorer;

    public AdvancedRetrievalService(VectorStorePort vectorStore, LexicalScorer lexicalScorer) {
        this.vectorStore = vectorStore;
        this.lexicalScorer = lexicalScorer;
    }

    public List<Chunk> retrieve(String query, int topK, Map<String, Object> filterValues) {
        return retrieve(query, topK, null, filterValues);
    }

    public List<Chunk> retrieve(String query, int topK, String documentId, Map<String, Object> filterValues) {
        var filter = MetadataFilter.of(filterValues);
        var candidates = candidateSearch(query, topK, documentId);
        var matches = filter.isEmpty()
                ? candidates
                : candidates.stream().filter(filter::matches).toList();
        log.info("Retrieved {} candidates, {} after metadata filter", candidates.size(), matches.size());
        if (matches.size() <= topK) {
            return matches;
        }
        var fused = fusedRrf(lexicalScorer.score(query, matches));
        return topByRrf(matches, fused, topK);
    }

    private List<Chunk> candidateSearch(String query, int topK, String documentId) {
        int candidateK = topK * CANDIDATE_MULTIPLIER;
        return documentId == null || documentId.isBlank()
                ? vectorStore.similaritySearch(query, candidateK)
                : vectorStore.similaritySearch(query, candidateK, documentId);
    }

    private static double[] fusedRrf(double[] lexicalScores) {
        var ranks = lexicalRanks(lexicalScores);
        return IntStream.range(0, ranks.length)
                .mapToDouble(i -> weight(i) + weight(ranks[i]))
                .toArray();
    }

    private static int[] lexicalRanks(double[] scores) {
        var n = scores.length;
        var idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(scores[b], scores[a]));
        var ranks = new int[n];
        for (int pos = 0; pos < n; pos++) {
            ranks[idx[pos]] = pos;
        }
        return ranks;
    }

    private static double weight(int rankPosition) {
        return 1.0d / (RRF_K + rankPosition + 1);
    }

    private static List<Chunk> topByRrf(List<Chunk> matches, double[] fused, int topK) {
        return IntStream.range(0, matches.size())
                .boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> -fused[i]).thenComparingInt(i -> i))
                .limit(topK)
                .map(matches::get)
                .toList();
    }

    public List<DocumentSummary> listDocuments() {
        return vectorStore.listDocuments();
    }

    public void delete(String documentId) {
        vectorStore.delete(documentId);
        log.info("Deleted document {}", documentId);
    }
}