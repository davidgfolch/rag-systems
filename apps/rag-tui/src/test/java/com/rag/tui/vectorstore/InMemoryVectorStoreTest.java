package com.rag.tui.vectorstore;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.EmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryVectorStoreTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final VectorStore sut = new InMemoryVectorStore(embeddingModel);

    @Test
    void retrievesMostSimilarChunk() {
        Chunk match = chunk("c1", "matching content", List.of(1.0f, 0.0f));
        Chunk other = chunk("c2", "other content", List.of(0.0f, 1.0f));
        sut.add(List.of(match, other));

        when(embeddingModel.embed("query")).thenReturn(List.of(1.0f, 0.1f));

        List<Chunk> results = sut.similaritySearch("query", 1);
        assertThat(results).containsExactly(match);
    }

    @Test
    void rejectsChunkWithoutEmbedding() {
        Chunk missing = new Chunk("c9", "d1", "no embedding", 0, Map.of());
        assertThatThrownBy(() -> sut.add(List.of(missing)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Chunk chunk(String id, String content, List<Float> embedding) {
        Chunk c = new Chunk(id, "d1", content, 0, Map.of());
        c.setEmbedding(embedding);
        return c;
    }
}
