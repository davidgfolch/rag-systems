package com.rag.tui.chunking;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.services.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recursive character splitter. Tries paragraph → sentence → word boundaries in
 * order, only splitting at a finer level when needed.
 */
public class RecursiveCharacterChunker implements TextSplitter {

    private static final String[] SEPARATORS = {"\n\n", "\n", ". ", "? ", "! ", " ", ""};

    private final int maxChunkSize;
    private final int overlap;

    public RecursiveCharacterChunker(int maxChunkSize, int overlap) {
        if (maxChunkSize <= 0) throw new IllegalArgumentException("maxChunkSize must be > 0");
        this.maxChunkSize = maxChunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<Chunk> split(Document document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) return List.of();

        List<String> raw = new ArrayList<>();
        splitRecursively(content, SEPARATORS, 0, raw);

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (String piece : raw) {
            if (piece.isBlank()) continue;
            chunks.add(new Chunk(UUID.randomUUID().toString(), document.getId(), piece.trim(),
                    index++, Map.of("strategy", "recursive", "maxChunkSize", maxChunkSize)));
        }
        return chunks;
    }

    private void splitRecursively(String text, String[] separators, int sepIndex, List<String> result) {
        if (text.length() <= maxChunkSize) {
            result.add(text);
            return;
        }
        if (separators[sepIndex].isEmpty()) {
            for (int i = 0; i < text.length(); i += maxChunkSize) {
                result.add(text.substring(i, Math.min(i + maxChunkSize, text.length())));
            }
            return;
        }
        String[] parts = text.split(java.util.regex.Pattern.quote(separators[sepIndex]), -1);
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String candidate = current.isEmpty() ? part : current + separators[sepIndex] + part;
            if (candidate.length() <= maxChunkSize) {
                current = new StringBuilder(candidate);
            } else if (!current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder(part);
                if (part.length() > maxChunkSize) {
                    splitRecursively(part, separators, sepIndex + 1, result);
                    current = new StringBuilder();
                }
            } else if (part.length() > maxChunkSize) {
                splitRecursively(part, separators, sepIndex + 1, result);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
    }
}
