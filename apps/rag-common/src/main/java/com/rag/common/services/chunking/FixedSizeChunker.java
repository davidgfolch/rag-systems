package com.rag.common.services.chunking;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rag.common.domain.MetadataKeys.END;
import static com.rag.common.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.domain.MetadataKeys.START;
import static com.rag.common.domain.MetadataKeys.STRATEGY;

/**
 * Fixed-size sliding window chunker. Splits text into fixed-size pieces with overlap.
 *
 * <p>Best for homogeneous text (reviews, logs) where structure doesn't matter.
 * Baseline for comparison against recursive and semantic chunking strategies.
 */
public class FixedSizeChunker implements TextSplitter {

    private static final Logger log = LoggerFactory.getLogger(FixedSizeChunker.class);

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

        var baseMeta = documentMeta(document);
        var chunks = new ArrayList<Chunk>();
        int start = 0;
        int index = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            var chunkContent = content.substring(start, end).trim();

            if (!chunkContent.isEmpty()) {
                var meta = new HashMap<>(baseMeta);
                meta.put(STRATEGY, "fixed");
                meta.put(START, start);
                meta.put(END, end);
                chunks.add(new Chunk(
                        UUID.randomUUID().toString(),
                        document.getId(),
                        chunkContent,
                        index++,
                        Map.copyOf(meta)
                ));
            }

            start += chunkSize - overlap;
            if (start >= content.length()) break;
        }

        log.debug("Fixed-size chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private static Map<String, Object> documentMeta(Document document) {
        var meta = new HashMap<>(document.getMetadata());
        meta.remove(RAW_BYTES);
        return meta;
    }
}