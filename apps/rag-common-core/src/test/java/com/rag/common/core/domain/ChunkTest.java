package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkTest {

    @Test
    void shouldStoreEmbeddingOnceAssigned() {
        Chunk sut = new Chunk("chunk-1", "doc-1", "text", 0, Map.of("k", "v"));
        var embedding = new float[]{0.1f, 0.2f, 0.3f};
        sut.setEmbedding(embedding);
        assertThat(sut.getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(sut.getDocumentId()).isEqualTo("doc-1");
        assertThat(sut.getIndex()).isZero();
        assertThat(sut.getMetadata()).containsEntry("k", "v");
    }

    @Test
    void shouldHaveNullEmbeddingInitially() {
        Chunk sut = new Chunk("chunk-1", "doc-1", "text", 1, null);
        assertThat(sut.getEmbedding()).isNull();
    }
}