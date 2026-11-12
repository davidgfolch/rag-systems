package com.rag.common.core.services;

import java.util.List;

/**
 * Strategy interface (port) for generating embeddings from text.
 *
 * <p>Implementations wrap different providers (Ollama, OpenAI, HuggingFace),
 * selected via configuration profiles. Business logic depends on this
 * interface only (DIP, OCP), never on a concrete provider.
 *
 * <p>Vectors are primitive {@code float[]} rather than boxed {@code List<Float>}:
 * a 768-dimension embedding costs ~3 KB instead of ~12 KB and avoids the
 * per-element unboxing on every similarity computation.
 */
@FunctionalInterface
public interface EmbeddingModelPort {

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text the input text
     * @return the embedding vector
     */
    float[] embed(String text);

    /**
     * Generates embedding vectors for a batch of texts in a single provider
     * call. The default implementation delegates to {@link #embed(String)}
     * per text; adapters backed by a batch-capable provider override it.
     *
     * @param texts the input texts
     * @return one embedding vector per input text, in the same order
     */
    default List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
