package com.rag.common.core.services;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;

import java.util.List;

/**
 * Strategy interface for format-aware chunking. Implementations receive the
 * normalized document so they can respect structural boundaries instead of
 * splitting flattened text.
 */
public interface DocumentChunker {

    boolean supports(ParsedDocument document);

    List<Chunk> chunk(ParsedDocument document, ChunkingConfig config);
}
