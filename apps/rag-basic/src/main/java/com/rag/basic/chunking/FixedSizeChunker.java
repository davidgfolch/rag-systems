package com.rag.basic.chunking;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.services.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixed-size sliding window chunker. Splits text into fixed-size pieces with overlap.
 *
 * <p>Best for homogeneous text (reviews, logs) where structure doesn't matter.
 * Baseline for comparison against recursive and semantic chunking strategies.
 */
public class FixedSizeChunker implements TextSplitter {

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
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkContent = content.substring(start, end).trim();

            if (!chunkContent.isEmpty()) {
                chunks.add(new Chunk(
                        UUID.randomUUID().toString(),
                        document.getId(),
                        chunkContent,
                        index++,
                        Map.of("strategy", "fixed", "start", start, "end", end)
                ));
            }

            start += chunkSize - overlap;
            if (start >= content.length()) break;
        }

        return chunks;
    }
}