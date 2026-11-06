package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Selects the structural chunker that understands the parsed document's format,
 * keeping every format's rules isolated while exposing one strategy bean.
 */
public class FormatAwareChunkerDispatcher implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(FormatAwareChunkerDispatcher.class);

    private final List<DocumentChunker> chunkers;

    public FormatAwareChunkerDispatcher(List<DocumentChunker> chunkers) {
        if (chunkers == null || chunkers.isEmpty()) {
            throw new IllegalArgumentException("at least one chunker is required");
        }
        this.chunkers = List.copyOf(chunkers);
    }

    @Override
    public boolean supports(ParsedDocument document) {
        return chunkers.stream().anyMatch(chunker -> chunker.supports(document));
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document, ChunkingConfig config) {
        var selected = select(document);
        log.debug("Dispatching {} document {} to {}", document.sourceType(),
                document.documentId(), selected.getClass().getSimpleName());
        return selected.chunk(document, config);
    }

    private DocumentChunker select(ParsedDocument document) {
        return chunkers.stream()
                .filter(chunker -> chunker.supports(document))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No chunker supports format " + document.sourceType()));
    }
}
