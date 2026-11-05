package com.rag.contract.pipeline;

import java.util.Locale;
import java.util.Optional;

/**
 * Chunking strategy a pipeline variant runs. {@code SEMANTIC} cuts at topic
 * boundaries detected from the embeddings of neighbouring sentences.
 */
public enum ChunkingStrategy {
    RECURSIVE,
    TOKEN,
    FIXED,
    SEMANTIC;

    /** Parses a case-insensitive strategy name, empty when the name is unknown. */
    public static Optional<ChunkingStrategy> from(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
