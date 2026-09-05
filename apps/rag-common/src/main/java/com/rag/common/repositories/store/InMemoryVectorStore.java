package com.rag.common.repositories.store;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store using cosine similarity. Suitable for unit tests,
 * demos, and comparing strategies without requiring a running PgVector instance.
 *
 * <p>Embeds queries via the injected {@link EmbeddingModel}. Not persisted; for
 * durable storage use {@link PgVectorStoreAdapter} (Spring AI), selected via
 * configuration.
 */
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, Chunk> chunksById = new ConcurrentHashMap<>();
    private final EmbeddingModel embeddingModel;

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
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
        log.debug("In-memory store now holds {} chunks", chunksById.size());
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        return search(embeddingModel.embed(query), topK);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        List<Float> queryEmbedding = embeddingModel.embed(query);
        return search(queryEmbedding, topK).stream()
                .filter(chunk -> chunk.getDocumentId().equals(documentId))
                .toList();
    }

    public int size() {
        return chunksById.size();
    }

    @Override
    public List<DocumentSummary> listDocuments() {
        Map<String, List<Chunk>> byDoc = chunksById.values().stream()
                .collect(Collectors.groupingBy(Chunk::getDocumentId, LinkedHashMap::new, Collectors.toList()));
        return byDoc.entrySet().stream()
                .map(e -> new DocumentSummary(e.getKey(), e.getValue().size(), firstMetadata(e.getValue())))
                .toList();
    }

    private static Map<String, Object> firstMetadata(List<Chunk> chunks) {
        Chunk first = chunks.stream()
                .filter(c -> !c.getMetadata().isEmpty())
                .findFirst()
                .orElse(null);
        return first == null ? Map.of() : first.getMetadata();
    }

    private List<Chunk> search(List<Float> queryEmbedding, int topK) {
        List<Chunk> hits = chunksById.values().stream()
                .map(chunk -> new Scored(chunk, cosine(queryEmbedding, chunk.getEmbedding())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(Scored::chunk)
                .toList();
        log.debug("In-memory search returned {} hits", hits.size());
        return hits;
    }

    private static double cosine(List<Float> a, List<Float> b) {
        int n = Math.min(a.size(), b.size());
        double dot = 0;
        double normA = 0;
        double normB = 0;        for (int i = 0; i < n; i++) {
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