package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.rag.common.core.domain.MetadataKeys.END;
import static com.rag.common.core.domain.MetadataKeys.START;
import static com.rag.common.core.domain.MetadataKeys.STRATEGY;

/**
 * Fixed-size sliding window chunker. Splits text into fixed-size pieces with overlap.
 *
 * <p>Best for homogeneous text (reviews, logs) where structure doesn't matter.
 * Baseline for comparison against recursive and semantic chunking strategies.
 */
public class FixedSizeChunker implements TextSplitter {

    private static final Logger log = LoggerFactory.getLogger(FixedSizeChunker.class);
    private static final String STRATEGY_NAME = "fixed";

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<Chunk> split(Document document) {
        var content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }
        var chunks = ChunkerSupport.chunks(document, ChunkerSupport.documentMeta(document), windows(content).toList());
        log.debug("Fixed-size chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private Stream<ChunkerSupport.Draft> windows(String content) {
        var step = chunkSize - overlap;
        return IntStream.iterate(0, start -> start < content.length(), start -> start + step)
                .mapToObj(start -> window(content, start));
    }

    private ChunkerSupport.Draft window(String content, int start) {
        var end = Math.min(start + chunkSize, content.length());
        return new ChunkerSupport.Draft(content.substring(start, end),
                Map.of(STRATEGY, STRATEGY_NAME, START, start, END, end));
    }
}
