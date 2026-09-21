package com.rag.provider.services;

import com.rag.common.domain.FloatConversions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding generation against the currently active embedding model.
 */
public class EmbeddingService {

    public static final int DEFAULT_EMBED_BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ModelRouter router;
    private final int maxEmbeddingBatchSize;

    public EmbeddingService(ModelRouter router) {
        this(router, DEFAULT_EMBED_BATCH_SIZE);
    }

    public EmbeddingService(ModelRouter router, int maxEmbeddingBatchSize) {
        this.router = router;
        this.maxEmbeddingBatchSize = requirePositive(maxEmbeddingBatchSize);
    }

    public List<List<Float>> embed(List<String> texts) {
        var vectors = new ArrayList<List<Float>>();
        for (int from = 0; from < texts.size(); from += maxEmbeddingBatchSize) {
            var window = texts.subList(from, Math.min(texts.size(), from + maxEmbeddingBatchSize));
            vectors.addAll(embedWindow(window));
        }
        log.info("Embedded {} texts at dimension {}", texts.size(), router.embeddingDimension());
        return vectors;
    }

    private List<List<Float>> embedWindow(List<String> texts) {
        log.debug("Embedding window of {} texts", texts.size());
        var response = router.embeddingModel().call(new EmbeddingRequest(texts, null));
        return response.getResults().stream().map(Embedding::getOutput)
                .map(FloatConversions::toFloatList).toList();
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("embedding batch size must be positive");
        }
        return value;
    }
}