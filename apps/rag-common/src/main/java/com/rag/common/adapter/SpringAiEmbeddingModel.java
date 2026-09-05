package com.rag.common.adapter;

import com.rag.common.services.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Adapter bridging the domain {@link EmbeddingModel} interface onto Spring AI's
 * {@link org.springframework.ai.embedding.EmbeddingModel}. Keeps business logic
 * decoupled from the concrete provider (DIP): the Spring AI bean resolves to
 * Ollama, OpenAI, etc. based on the active profile.
 */
public class SpringAiEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingModel.class);

    private final org.springframework.ai.embedding.EmbeddingModel delegate;

    public SpringAiEmbeddingModel(org.springframework.ai.embedding.EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Float> embed(String text) {
        EmbeddingResponse response = delegate.call(new EmbeddingRequest(List.of(text), null));
        float[] output = response.getResult().getOutput();
        log.debug("Embedded text ({} chars) -> {} dimensions", text.length(), output.length);
        java.util.List<Float> out = new java.util.ArrayList<>(output.length);
        for (float v : output) out.add(v);
        return out;
    }
}