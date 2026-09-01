package com.rag.common.services.chunking;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.services.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token-aware chunker. Splits on token boundaries rather than characters,
 * using a simple word-based tokenizer approximation (1 token ≈ 4 characters
 * for English text, per the common heuristic used by the predecessor models).
 *
 * <p>Provides the most predictable chunk size for downstream LLM context
 * windows, at the cost of occasionally splitting mid-sentence.
 */
public class TokenChunker implements TextSplitter {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+\\s*");

    private final int maxTokens;
    private final int overlapTokens;

    public TokenChunker(int maxTokens, int overlapTokens) {
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be > 0");
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<Chunk> split(Document document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            tokens.add(matcher.group().trim());
        }

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;

        while (start < tokens.size()) {
            int end = Math.min(start + maxTokens, tokens.size());
            String chunkContent = String.join(" ", tokens.subList(start, end)).trim();

            if (!chunkContent.isEmpty()) {
                chunks.add(new Chunk(
                        UUID.randomUUID().toString(),
                        document.getId(),
                        chunkContent,
                        index++,
                        Map.of("strategy", "token", "maxTokens", maxTokens, "tokenCount", end - start)
                ));
            }

            start += maxTokens - overlapTokens;
            if (start >= tokens.size()) break;
        }

        return chunks;
    }
}