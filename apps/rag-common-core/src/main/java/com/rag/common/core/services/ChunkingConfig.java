package com.rag.common.core.services;

/**
 * Format-aware chunking options. The variant still owns the strategy and the
 * character window; this record only carries the structural policy.
 *
 * @param maxRecordsPerChunk      sibling JSON records grouped into one chunk
 * @param maxTokens               hard token cap, {@code 0} derives it from the variant window
 * @param preserveTables          keep tables intact unless they exceed the cap
 * @param preserveCode            keep fenced code blocks intact unless they exceed the cap
 * @param respectSectionBoundaries never group blocks across different sections
 */
public record ChunkingConfig(int maxRecordsPerChunk, int maxTokens, boolean preserveTables,
                             boolean preserveCode, boolean respectSectionBoundaries) {

    public static final int CHARS_PER_TOKEN = 4;
    public static final int DEFAULT_MAX_RECORDS_PER_CHUNK = 5;
    public static final int NO_TOKEN_LIMIT = 0;
    public static final ChunkingConfig DEFAULT =
            new ChunkingConfig(DEFAULT_MAX_RECORDS_PER_CHUNK, NO_TOKEN_LIMIT, true, true, true);

    public ChunkingConfig {
        if (maxRecordsPerChunk <= 0) {
            throw new IllegalArgumentException("maxRecordsPerChunk must be > 0");
        }
        if (maxTokens < 0) {
            throw new IllegalArgumentException("maxTokens must be >= 0");
        }
    }

    /** Character cap to enforce, derived from the token cap when configured. */
    public int effectiveMaxChars(int variantMaxChars) {
        if (variantMaxChars <= 0) {
            throw new IllegalArgumentException("variantMaxChars must be > 0");
        }
        return maxTokens > 0 ? Math.min(variantMaxChars, maxTokens * CHARS_PER_TOKEN) : variantMaxChars;
    }
}
