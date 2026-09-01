package com.rag.common.repositories.store;

import com.rag.common.domain.Chunk;
import com.rag.common.services.EmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryVectorStoreTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final InMemoryVectorStore store = new InMemoryVectorStore(embeddingModel);

    @Test
    void storesAndRetrievesBySimilarity() {
        when(embeddingModel.embed(anyString())).thenReturn(List.of(0.9f, 0.1f));
        Chunk aboutDogs = chunk("c1", new float[]{1, 0});
        Chunk aboutCats = chunk("c2", new float[]{0, 1});
        store.add(List.of(aboutDogs, aboutCats));

        List<Chunk> results = store.similaritySearch("dogs", 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("c1");
    }

    @Test
    void respectsTopK() {
        when(embeddingModel.embed(anyString())).thenReturn(List.of(1f, 0f));
        store.add(List.of(chunk("c1", new float[]{1, 0}),
                chunk("c2", new float[]{0.8f, 0.2f}),
                chunk("c3", new float[]{0, 1})));

        assertThat(store.similaritySearch("dogs", 2)).hasSize(2);
    }

    @Test
    void returnsEmptyForEmptyStore() {
        when(embeddingModel.embed(anyString())).thenReturn(List.of(1f, 0f));
        assertThat(store.similaritySearch("dogs", 5)).isEmpty();
    }

    @Test
    void rejectsChunkWithoutEmbedding() {
        Chunk noEmbedding = new Chunk("c1", "d1", "text", 0, Map.of());
        List<Chunk> chunks = List.of(noEmbedding);
        assertThatThrownBy(() -> store.add(chunks))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesSize() {
        store.add(List.of(chunk("c1", new float[]{1, 0})));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void filtersByDocumentId() {
        when(embeddingModel.embed(anyString())).thenReturn(List.of(1f, 0f));
        Chunk a = chunk("c1", new float[]{1, 0});
        Chunk b = chunk("c2", new float[]{1, 0});
        Chunk otherDoc = new Chunk("c3", "d2", "text", 0, Map.of());
        otherDoc.setEmbedding(List.of(1f, 0f));
        store.add(List.of(a, b, otherDoc));

        List<Chunk> results = store.similaritySearch("q", 5, "d1");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getDocumentId().equals("d1"));
    }

    private static Chunk chunk(String id, float[] embedding) {
        Chunk c = new Chunk(id, "d1", "text " + id, 0, Map.of());
        c.setEmbedding(floatList(embedding));
        return c;
    }

    private static List<Float> floatList(float[] values) {
        List<Float> out = new java.util.ArrayList<>(values.length);
        for (float v : values) out.add(v);
        return out;
    }
}