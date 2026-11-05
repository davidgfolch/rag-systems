package com.rag.contract.provider;

import java.util.List;

/**
 * Embedding computation request for one or more texts. {@code model} is
 * optional: blank/null means the provider's active embedding model, while a
 * model id lets one module embed a pipeline variant with its own model.
 */
public record EmbedRequest(String model, List<String> texts) {

    public EmbedRequest(List<String> texts) {
        this(null, texts);
    }
}