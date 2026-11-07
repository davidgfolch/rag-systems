package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.rag.common.core.domain.MetadataKeys.MAX_CHUNK_SIZE;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.core.domain.MetadataKeys.STRATEGY;

/**
 * Semantic chunker. Splits a document into sentences, embeds them, and starts a
 * new chunk where the cosine distance between neighbouring sentences reaches
 * the configured percentile of the document's own distances — a topic shift.
 *
 * <p>{@code maxChunkSize} and {@code overlap} stay as an upper-bound safety
 * net: a group of sentences that would outgrow the window is cut anyway, and a
 * single oversized sentence is hard-split. Boundaries come from the document
 * itself, so a text with a uniform distance profile is not cut into one chunk
 * per sentence.
 *
 * <p>Sentences are embedded once per document before the chunks are embedded
 * for storage, so ingesting with this chunker costs roughly one extra
 * embedding per sentence.
 */
public class SemanticChunker implements TextSplitter {

    public static final double DEFAULT_BREAKPOINT_PERCENTILE = 95.0;

    private static final Logger log = LoggerFactory.getLogger(SemanticChunker.class);

    private static final String STRATEGY_NAME = "semantic";
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\n\\s*\\n");
    private static final double PERCENT_SCALE = 100.0;
    private static final float IDENTICAL_DISTANCES_EPSILON = 1e-6f;
    static final int EMBED_BATCH_SIZE = 256;

    private final EmbeddingModelPort embeddings;
    private final int maxChunkSize;
    private final int overlap;
    private final double breakpointPercentile;

    public SemanticChunker(EmbeddingModelPort embeddings, int maxChunkSize, int overlap) {
        this(embeddings, maxChunkSize, overlap, DEFAULT_BREAKPOINT_PERCENTILE);
    }

    public SemanticChunker(EmbeddingModelPort embeddings, int maxChunkSize, int overlap,
                           double breakpointPercentile) {
        this.embeddings = Objects.requireNonNull(embeddings, "embedding model must not be null");
        if (maxChunkSize <= 0) throw new IllegalArgumentException("maxChunkSize must be > 0");
        if (overlap < 0 || overlap >= maxChunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < maxChunkSize");
        }
        if (breakpointPercentile <= 0 || breakpointPercentile > PERCENT_SCALE) {
            throw new IllegalArgumentException("breakpoint percentile must be in (0, 100]");
        }
        this.maxChunkSize = maxChunkSize;
        this.overlap = overlap;
        this.breakpointPercentile = breakpointPercentile;
    }

    @Override
    public List<Chunk> split(Document document) {
        var content = document.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        var baseMeta = documentMeta(document);
        var groups = groupByTopicShift(content);
        var chunks = new ArrayList<Chunk>();
        for (int i = 0; i < groups.size(); i++) {
            var meta = new HashMap<>(baseMeta);
            meta.put(STRATEGY, STRATEGY_NAME);
            meta.put(MAX_CHUNK_SIZE, maxChunkSize);
            chunks.add(new Chunk(
                    UUID.randomUUID().toString(),
                    document.getId(),
                    groups.get(i).trim(),
                    i,
                    Map.copyOf(meta)
            ));
        }

        log.debug("Semantic-chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private List<String> groupByTopicShift(String content) {
        if (content.length() <= maxChunkSize) {
            return List.of(content);
        }

        var sentences = sentences(content);
        var breakpoints = sentences.size() > 1 ? breakpoints(sentences) : Set.<Integer>of();
        var groups = new ArrayList<String>();
        var current = new StringBuilder();

        for (int i = 0; i < sentences.size(); i++) {
            var sentence = sentences.get(i);
            if (sentence.length() > maxChunkSize) {
                flush(current, groups);
                hardSplit(sentence, groups);
                continue;
            }
            if (!current.isEmpty()
                    && (breakpoints.contains(i) || current.length() + 1 + sentence.length() > maxChunkSize)) {
                flush(current, groups);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        flush(current, groups);
        return withOverlap(groups);
    }

    /** Sentence indices that start a new chunk, i.e. one past each topic shift. */
    private Set<Integer> breakpoints(List<String> sentences) {
        var vectors = embedAll(sentences);
        var distances = new float[vectors.size() - 1];
        for (int i = 1; i < vectors.size(); i++) {
            distances[i - 1] = 1f - cosine(vectors.get(i - 1), vectors.get(i));
        }

        var result = new HashSet<Integer>();
        if (spread(distances) <= IDENTICAL_DISTANCES_EPSILON) {
            return result;
        }
        var threshold = percentile(distances, breakpointPercentile);
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] >= threshold) {
                result.add(i + 1);
            }
        }
        return result;
    }

    private List<float[]> embedAll(List<String> sentences) {
        if (sentences.size() <= EMBED_BATCH_SIZE) {
            return embeddings.embed(sentences);
        }
        var vectors = new ArrayList<float[]>(sentences.size());
        for (int from = 0; from < sentences.size(); from += EMBED_BATCH_SIZE) {
            var to = Math.min(sentences.size(), from + EMBED_BATCH_SIZE);
            vectors.addAll(embeddings.embed(sentences.subList(from, to)));
        }
        return vectors;
    }

    private List<String> withOverlap(List<String> groups) {
        if (overlap <= 0 || groups.size() <= 1) {
            return groups;
        }
        var result = new ArrayList<String>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            var text = groups.get(i);
            if (i > 0) {
                var previous = groups.get(i - 1);
                text = previous.substring(Math.max(0, previous.length() - overlap)) + " " + text;
            }
            result.add(text);
        }
        return result;
    }

    private void hardSplit(String sentence, List<String> groups) {
        for (int start = 0; start < sentence.length(); start += maxChunkSize) {
            groups.add(sentence.substring(start, Math.min(start + maxChunkSize, sentence.length())));
        }
    }

    private static void flush(StringBuilder current, List<String> groups) {
        if (!current.isEmpty()) {
            groups.add(current.toString());
            current.setLength(0);
        }
    }

    private static List<String> sentences(String content) {
        var result = new ArrayList<String>();
        for (var part : SENTENCE_BOUNDARY.split(content)) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static float spread(float[] values) {
        var min = Float.MAX_VALUE;
        var max = -Float.MAX_VALUE;
        for (var value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    private static float percentile(float[] values, double percentile) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        var rank = (int) Math.ceil(percentile / PERCENT_SCALE * sorted.length);
        return sorted[Math.clamp((long) rank - 1, 0, sorted.length - 1)];
    }

    private static float cosine(float[] a, float[] b) {
        var dot = 0.0;
        var normA = 0.0;
        var normB = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static Map<String, Object> documentMeta(Document document) {
        var meta = new HashMap<>(document.getMetadata());
        meta.remove(RAW_BYTES);
        return meta;
    }
}
