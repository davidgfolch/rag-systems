package com.rag.common.repositories;

import com.rag.common.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreTest {

    @Test
    void defaultDocumentScopedSearchFiltersByDocumentId() {
        var other = new Chunk("c1", "d1", "text one", 0, Map.of());
        var wanted = new Chunk("c2", "d2", "text two", 1, Map.of());
        VectorStorePort store = new ChunkStore(List.of(other, wanted));

        var result = store.similaritySearch("query", 5, "d2");

        assertThat(result).containsExactly(wanted);
    }

    private record ChunkStore(List<Chunk> chunks) implements VectorStorePort {
        @Override
        public void add(List<Chunk> toAdd) {
            // no-op: this fixture only serves pre-populated chunks to similaritySearch
        }

        @Override
        public List<Chunk> similaritySearch(String query, int topK) {
            return chunks;
        }

        @Override
        public void delete(String documentId) {
            // no-op: this fixture only serves pre-populated chunks to similaritySearch
        }
    }
}