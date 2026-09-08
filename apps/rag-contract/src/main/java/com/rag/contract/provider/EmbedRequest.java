package com.rag.contract.provider;

import java.util.List;

/**
 * Embedding computation request for one or more texts.
 */
public record EmbedRequest(List<String> texts) {
}