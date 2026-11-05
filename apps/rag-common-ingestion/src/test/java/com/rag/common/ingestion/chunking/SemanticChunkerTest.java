package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.MetadataKeys;
import com.rag.common.core.services.EmbeddingModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticChunkerTest {

    private static final List<Float> TOPIC_ALPHA = List.of(1f, 0f);
    private static final List<Float> TOPIC_BETA = List.of(0f, 1f);

    private final AtomicInteger embeddingCalls = new AtomicInteger();

    @Test
    void cutsWhereTheTopicChanges() {
        var chunker = chunker(Map.of(
                "Alpha one is about retrieval.", TOPIC_ALPHA,
                "Alpha two repeats the point.", TOPIC_ALPHA,
                "Beta one switches the topic.", TOPIC_BETA,
                "Beta two continues it.", TOPIC_BETA), 80, 0);
        var chunks = chunker.split(new Document("d1", """
                Alpha one is about retrieval. Alpha two repeats the point. \
                Beta one switches the topic. Beta two continues it.""", Map.of()));
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent())
                .contains("Alpha one is about retrieval.", "Alpha two repeats the point.")
                .doesNotContain("Beta one");
        assertThat(chunks.get(1).getContent()).startsWith("Beta one switches the topic.");
    }

    @Test
    void capsEveryChunkAtTheWindowWhenNoTopicShiftIsDetected() {
        var chunker = chunker(Map.of(), 40, 0);
        var chunks = chunker.split(new Document("d1", """
                One is short. Two is short. Three is short. Four is short.""", Map.of()));
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getContent().length()).isLessThanOrEqualTo(40));
        assertThat(chunks.get(0).getContent()).startsWith("One is short.");
    }

    @Test
    void prefixesEachChunkWithThePreviousChunksOverlap() {
        var chunker = chunker(Map.of(), 40, 10);
        var chunks = chunker.split(new Document("d1", """
                One is short. Two is short. Three is short. Four is short.""", Map.of()));
        assertThat(chunks).hasSizeGreaterThan(1);
        var first = chunks.get(0).getContent();
        var tail = first.substring(first.length() - 10).trim();
        assertThat(chunks.get(1).getContent()).startsWith(tail + " ");
    }

    @Test
    void hardSplitsASingleSentenceLongerThanTheWindow() {
        var chunker = chunker(Map.of(), 40, 0);
        var chunks = chunker.split(new Document("d1", "X".repeat(100), Map.of()));
        assertThat(chunks).extracting(chunk -> chunk.getContent().length()).containsExactly(40, 40, 20);
    }

    @Test
    void keepsADocumentThatFitsTheWindowWholeWithoutEmbeddingIt() {
        var chunker = chunker(Map.of(), 512, 64);
        var chunks = chunker.split(new Document("d1", "Hello world", Map.of()));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("Hello world");
        assertThat(chunks.get(0).getMetadata()).containsEntry(MetadataKeys.STRATEGY, "semantic");
        assertThat(embeddingCalls).hasValue(0);
    }

    @Test
    void returnsNoChunksForBlankContent() {
        var chunker = chunker(Map.of(), 512, 64);
        assertThat(chunker.split(new Document("d1", "   ", Map.of()))).isEmpty();
        assertThat(embeddingCalls).hasValue(0);
    }

    @Test
    void rejectsInvalidWindowsAndPercentiles() {
        var embeddings = (EmbeddingModelPort) text -> TOPIC_ALPHA;
        assertThatThrownBy(() -> new SemanticChunker(embeddings, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunker(embeddings, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunker(embeddings, 10, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunker(embeddings, 10, 2, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunker(null, 10, 2))
                .isInstanceOf(NullPointerException.class);
    }

    private SemanticChunker chunker(Map<String, List<Float>> vectors, int maxChunkSize, int overlap) {
        EmbeddingModelPort embeddings = text -> {
            embeddingCalls.incrementAndGet();
            return vectors.getOrDefault(text, TOPIC_ALPHA);
        };
        return new SemanticChunker(embeddings, maxChunkSize, overlap);
    }
}
