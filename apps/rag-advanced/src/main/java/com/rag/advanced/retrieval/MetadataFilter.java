package com.rag.advanced.retrieval;

import com.rag.common.core.domain.Chunk;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable key/value predicate over chunk metadata. An empty map matches every
 * chunk; otherwise every key must be present with an equal value.
 */
public final class MetadataFilter {

    private final Map<String, Object> values;

    private MetadataFilter(Map<String, Object> values) {
        this.values = values;
    }

    public static MetadataFilter of(Map<String, Object> values) {
        return new MetadataFilter(values == null ? Map.of() : Map.copyOf(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean matches(Chunk chunk) {
        var metadata = chunk.getMetadata();
        return values.entrySet().stream()
                .allMatch(entry -> Objects.equals(entry.getValue(), metadata.get(entry.getKey())));
    }
}