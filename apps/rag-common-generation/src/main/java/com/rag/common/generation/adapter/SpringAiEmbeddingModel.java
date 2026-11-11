package com.rag.common.generation.adapter;

import com.rag.common.core.services.EmbeddingModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.List;

/**
 * Adapter bridging the domain {@link EmbeddingModelPort} interface onto Spring AI's
 * {@link EmbeddingModel}. Keeps business logic
 * decoupled from the concrete provider (DIP): the Spring AI bean resolves to
 * Ollama, OpenAI, etc. based on the active profile.
 */
public class SpringAiEmbeddingModel implements EmbeddingModelPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingModel.class);

    private final EmbeddingModel delegate;

    public SpringAiEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public float[] embed(String text) {
        var res = delegate.call(new EmbeddingRequest(List.of(text), null));
        var output = res.getResult().getOutput();
        log.debug("Embedded text ({} chars) -> {} dimensions", text.length(), output.length);
        return output;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        var res = delegate.call(new EmbeddingRequest(texts, null));
        var vectors = res.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
        log.debug("Batch embedded {} texts -> {} vectors", texts.size(), vectors.size());
        return vectors;
    }
}
