package com.rag.common.services;

import com.rag.common.domain.Document;

/**
 * Strategy interface for parsing documents of various formats
 * (PDF, DOCX, HTML, plain text) into plain readable text.
 */
public interface DocumentParser {

    /**
     * Parses a document and returns its textual content.
     *
     * @param document the source document
     * @return the extracted plain-text content
     */
    String parse(Document document);
}