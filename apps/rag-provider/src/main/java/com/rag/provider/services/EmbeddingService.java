package com.rag.provider.services;

import com.rag.common.domain.FloatConversions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;

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
                .map(FloatConversions::toFloatList).toList();
        log.info("Embedded {} texts at dimension {}", texts.size(), router.embeddingDimension());
        return vectors;
    }
}