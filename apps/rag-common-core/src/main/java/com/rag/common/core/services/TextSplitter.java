package com.rag.common.core.services;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;

import java.util.List;

/**
 * Strategy interface for splitting documents into chunks.
 *
 * <p>Implementations are swappable via configuration, enabling comparison of
 * fixed-size, recursive, semantic, and agentic chunking strategies.
 */
public interface TextSplitter {

    /**
     * Splits a document into chunks suitable for embedding.
     *
     * @param document the source document
     * @return ordered list of chunks
     */
    List<Chunk> split(Document document);
}