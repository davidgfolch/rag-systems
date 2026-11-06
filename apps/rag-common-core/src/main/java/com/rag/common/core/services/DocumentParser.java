package com.rag.common.core.services;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;

/**
 * Strategy interface for parsing documents into a structured, format-neutral
 * representation. Implementations keep the document hierarchy (sections,
 * tables, code, JSON records) instead of flattening it to text.
 */
public interface DocumentParser {

    /**
     * Whether this parser understands the given document. The default keeps
     * plain parsers usable as fallbacks.
     */
    default boolean supports(Document document) {
        return true;
    }

    /**
     * Parses a document, preserving its structure and provenance.
     *
     * @param document the source document (content and/or raw bytes)
     * @return the normalized parsed document
     */
    ParsedDocument parse(Document document);
}
