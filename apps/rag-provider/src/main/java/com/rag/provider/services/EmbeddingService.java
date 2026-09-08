package com.rag.provider.services;

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

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ModelRouter router;

    public EmbeddingService(ModelRouter router) {
        this.router = router;
    }

    public List<List<Float>> embed(List<String> texts) {
        var response = router.embeddingModel().call(new EmbeddingRequest(texts, null));
        var vectors = response.getResults().stream().map(Embedding::getOutput)
                .map(EmbeddingService::box).toList();
        log.info("Embedded {} texts at dimension {}", texts.size(), router.embeddingDimension());
        return vectors;
    }

    private static List<Float> box(float[] values) {
        var result = new ArrayList<Float>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}