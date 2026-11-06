package com.rag.common.core.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable, format-neutral representation of a parsed source document.
 * Preserves block order, section hierarchy and provenance without carrying the
 * original binary payload.
 */
public record ParsedDocument(String documentId, SourceType sourceType,
                             Map<String, Object> metadata, List<DocumentBlock> blocks) {

    private static final String BLOCK_SEPARATOR = "\n\n";

    public ParsedDocument {
        Objects.requireNonNull(documentId, "documentId must not be null");
        sourceType = sourceType == null ? SourceType.UNKNOWN : sourceType;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** Plain-text view used for empty-extraction validation and legacy callers. */
    public String text() {
        return blocks.stream()
                .map(DocumentBlock::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining(BLOCK_SEPARATOR));
    }

    public boolean isBlank() {
        return blocks.stream().allMatch(DocumentBlock::isBlank);
    }
}
