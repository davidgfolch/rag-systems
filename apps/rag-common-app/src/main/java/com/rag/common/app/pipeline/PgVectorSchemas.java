package com.rag.common.app.pipeline;

import com.rag.common.generation.adapter.RemoteEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Resolves the pgvector schema a module writes to: the configured override when
 * present, otherwise one schema per active embedding dimension so a dimension
 * switch never collides with vectors of another length.
 */
public final class PgVectorSchemas {

    private static final String DEFAULT_SCHEMA = "public";
    private static final String DIMENSION_SCHEMA_PREFIX = "rag_";

    private PgVectorSchemas() {
    }

    /** Schema-qualified, quoted identifier, e.g. {@code "rag_basic"."variant_v2"}. */
    public static String qualified(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    /** Quotes one identifier so it cannot be read as SQL syntax. */
    public static String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String resolve(String schemaOverride, EmbeddingModel model) {
        if (schemaOverride != null && !schemaOverride.isBlank()) {
            return schemaOverride.trim();
        }
        if (model instanceof RemoteEmbeddingModel remote) {
            return resolve(schemaOverride, remote.dimensions());
        }
        return DEFAULT_SCHEMA;
    }

    /** Variant-aware variant: its dimension is known up front, so no probe is needed. */
    public static String resolve(String schemaOverride, int dimension) {
        if (schemaOverride != null && !schemaOverride.isBlank()) {
            return schemaOverride.trim();
        }
        return dimension > 0 ? DIMENSION_SCHEMA_PREFIX + dimension : DEFAULT_SCHEMA;
    }
}
