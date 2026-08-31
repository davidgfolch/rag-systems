package com.rag.basic.embedding;

import com.rag.common.services.EmbeddingModel;
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

    private final org.springframework.ai.embedding.EmbeddingModel delegate;

    public SpringAiEmbeddingModel(org.springframework.ai.embedding.EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Float> embed(String text) {
        EmbeddingResponse response = delegate.call(new EmbeddingRequest(List.of(text), null));
        float[] output = response.getResult().getOutput();
        return box(output);
    }

    private static List<Float> box(float[] values) {
        java.util.List<Float> out = new java.util.ArrayList<>(values.length);
        for (float v : values) out.add(v);
        return out;
    }
}