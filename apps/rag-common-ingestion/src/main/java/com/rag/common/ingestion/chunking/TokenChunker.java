package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.rag.common.core.domain.MetadataKeys.MAX_TOKENS;
import static com.rag.common.core.domain.MetadataKeys.STRATEGY;
import static com.rag.common.core.domain.MetadataKeys.TOKEN_COUNT;

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
    private static final String STRATEGY_NAME = "token";

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
        var tokens = tokens(content);
        var chunks = ChunkerSupport.chunks(document, ChunkerSupport.documentMeta(document), windows(tokens).toList());
        log.debug("Token-chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private static List<String> tokens(String content) {
        return TOKEN_PATTERN.matcher(content).results()
                .map(MatchResult::group)
                .map(String::trim)
                .toList();
    }

    private Stream<ChunkerSupport.Draft> windows(List<String> tokens) {
        var step = maxTokens - overlapTokens;
        return IntStream.iterate(0, start -> start < tokens.size(), start -> start + step)
                .mapToObj(start -> window(tokens, start));
    }

    private ChunkerSupport.Draft window(List<String> tokens, int start) {
        var end = Math.min(start + maxTokens, tokens.size());
        return new ChunkerSupport.Draft(String.join(" ", tokens.subList(start, end)),
                Map.of(STRATEGY, STRATEGY_NAME, MAX_TOKENS, maxTokens, TOKEN_COUNT, end - start));
    }
}
