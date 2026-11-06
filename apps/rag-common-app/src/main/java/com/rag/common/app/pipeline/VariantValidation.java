package com.rag.common.app.pipeline;

import com.rag.contract.pipeline.ChunkingStrategy;
import com.rag.contract.pipeline.PipelineVariantDTO;

import java.util.regex.Pattern;

/** Validation rules shared by variant creation and update. */
final class VariantValidation {

    private static final Pattern VARIANT_ID = Pattern.compile("\\w{1,60}");

    private VariantValidation() {
    }

    static void requireValidId(String variantId) {
        if (variantId == null || !VARIANT_ID.matcher(variantId).matches()) {
            throw new IllegalArgumentException("Invalid variant id: " + variantId);
        }
    }

    static void requireValidWindow(PipelineVariantDTO request) {
        var strategy = ChunkingStrategy.from(request.strategy());
        if (strategy.isEmpty()) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + request.strategy());
        }
        if (request.chunkSize() <= 0 || request.chunkOverlap() < 0
                || request.chunkOverlap() >= request.chunkSize()) {
            throw new IllegalArgumentException("Chunk window must satisfy size > 0 and 0 <= overlap < size");
        }
        if (strategy.get() == ChunkingStrategy.TOKEN
                && request.chunkSize() / Chunkers.CHARS_PER_TOKEN
                <= request.chunkOverlap() / Chunkers.CHARS_PER_TOKEN) {
            throw new IllegalArgumentException("Token variants need size / 4 > overlap / 4");
        }
    }
}
