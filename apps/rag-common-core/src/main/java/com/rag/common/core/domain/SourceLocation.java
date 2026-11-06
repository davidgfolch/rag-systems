package com.rag.common.core.domain;

/**
 * Provenance of a block inside its source. Every field is optional and typed,
 * so consumers never have to interpret an unstructured metadata map.
 */
public record SourceLocation(Integer page, String domPath, String jsonPath,
                             Integer startOffset, Integer endOffset) {

    public static final SourceLocation NONE = new SourceLocation(null, null, null, null, null);

    public boolean isEmpty() {
        return page == null && domPath == null && jsonPath == null
                && startOffset == null && endOffset == null;
    }
}
