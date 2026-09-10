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
import java.util.regex.Pattern;

import static com.rag.common.domain.MetadataKeys.MAX_TOKENS;
import static com.rag.common.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.domain.MetadataKeys.STRATEGY;
import static com.rag.common.domain.MetadataKeys.TOKEN_COUNT;

/**
 * Token-aware chunker. Splits on token boundaries rather than characters,
 * using a simple word-based tokenizer approximation (1 token ≈ 4 characters
 * for English text, per the common heuristic used by the predecessor models).
 *
 * <p>Provides the most predictable chunk size for downstream LLM context
 * windows, at the cost of occasionally splitting mid-sentence.
 */
public class TokenChunker implements TextSplitter {

    private static final Logger log = LoggerFactory.getLogger(TokenChunker.class);

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
        var content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        var tokens = new ArrayList<String>();
        var matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            tokens.add(matcher.group().trim());
        }

        var baseMeta = documentMeta(document);
        var chunks = new ArrayList<Chunk>();
        int index = 0;
        int start = 0;

        while (start < tokens.size()) {
            int end = Math.min(start + maxTokens, tokens.size());
            var chunkContent = String.join(" ", tokens.subList(start, end)).trim();

            if (!chunkContent.isEmpty()) {
                var meta = new HashMap<>(baseMeta);
                meta.put(STRATEGY, "token");
                meta.put(MAX_TOKENS, maxTokens);
                meta.put(TOKEN_COUNT, end - start);
                chunks.add(new Chunk(
                        UUID.randomUUID().toString(),
                        document.getId(),
                        chunkContent,
                        index++,
                        Map.copyOf(meta)
                ));
            }

            start += maxTokens - overlapTokens;
            if (start >= tokens.size()) break;
        }

        log.debug("Token-chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private static Map<String, Object> documentMeta(Document document) {
        var meta = new HashMap<>(document.getMetadata());
        meta.remove(RAW_BYTES);
        return meta;
    }
}