package com.rag.basic.vectorstore;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.EmbeddingModel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector store using cosine similarity. Suitable for unit tests,
 * demos, and comparing strategies without requiring a running PgVector instance.
 *
 * <p>Embeds queries via the injected {@link EmbeddingModel}. Not persisted; for
 * durable storage use {@code PgVectorStoreAdapter} (Spring AI), selected via
 * configuration.
 */
public class SimpleVectorStore implements VectorStore {

    private final Map<String, Chunk> chunksById = new ConcurrentHashMap<>();
    private final EmbeddingModel embeddingModel;

    public SimpleVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (chunk.getEmbedding() == null) {
                throw new IllegalArgumentException("Chunk " + chunk.getId() + " has no embedding");
            }
            chunksById.put(chunk.getId(), chunk);
        }
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        List<Float> queryEmbedding = embeddingModel.embed(query);
        return chunksById.values().stream()
                .map(chunk -> new Scored(chunk, cosine(queryEmbedding, chunk.getEmbedding())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(Scored::chunk)
                .toList();
    }

    public int size() {
        return chunksById.size();
    }

    private static double cosine(List<Float> a, List<Float> b) {
        int n = Math.min(a.size(), b.size());
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < n; i++) {
            double av = a.get(i);
            double bv = b.get(i);
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Scored(Chunk chunk, double score) {}
}