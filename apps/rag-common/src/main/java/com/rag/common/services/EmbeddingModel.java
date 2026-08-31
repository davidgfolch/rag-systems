package com.rag.common.services;

import java.util.List;

/**
 * Strategy interface for generating embeddings from text.
 *
 * <p>Implementations wrap different providers (Ollama, OpenAI, HuggingFace),
 * selected via configuration profiles. Business logic depends on this
 * interface only (DIP, OCP), never on a concrete provider.
 */
public interface EmbeddingModel {

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text the input text
     * @return the embedding vector
     */
    List<Float> embed(String text);
}