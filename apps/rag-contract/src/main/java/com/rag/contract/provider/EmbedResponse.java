package com.rag.contract.provider;

import java.util.List;

/**
 * Embedding computation response, one vector per requested text.
 */
public record EmbedResponse(List<List<Float>> embeddings) {
}