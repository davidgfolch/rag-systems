package com.rag.common.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkTest {

    @Test
    void shouldStoreEmbeddingOnceAssigned() {
        Chunk sut = new Chunk("chunk-1", "doc-1", "text", 0, Map.of("k", "v"));
        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);

        sut.setEmbedding(embedding);

        assertThat(sut.getEmbedding()).isEqualTo(embedding);
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