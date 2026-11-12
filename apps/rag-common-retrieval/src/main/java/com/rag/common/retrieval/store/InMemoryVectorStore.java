package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.EmbeddingModelPort;
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
 * <p>Embeds queries via the injected {@link EmbeddingModelPort}. Not persisted; for
 * durable storage use {@link PgVectorStoreAdapter} (Spring AI), selected via
 * configuration.
 */
public class InMemoryVectorStore implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, Chunk> chunksById = new ConcurrentHashMap<>();
    private final EmbeddingModelPort embeddingModel;

    public InMemoryVectorStore(EmbeddingModelPort embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(List<Chunk> chunks) {
        chunks.forEach(chunk -> {
            if (chunk.getEmbedding() == null) {
                throw new IllegalArgumentException("Chunk " + chunk.getId() + " has no embedding");
            }
            chunksById.put(chunk.getId(), chunk);
        });
        log.debug("In-memory store now holds {} chunks", chunksById.size());
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        return search(embeddingModel.embed(query), topK, null);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        return search(embeddingModel.embed(query), topK, documentId);
    }

    public int size() {
        return chunksById.size();
    }

    @Override
    public void delete(String documentId) {
        chunksById.values().removeIf(chunk -> chunk.getDocumentId().equals(documentId));
        log.debug("Deleted document {} from in-memory store ({} chunks remain)", documentId, chunksById.size());
    }

    @Override
    public List<DocumentSummary> listDocuments() {
        var byDoc = chunksById.values().stream()
                .collect(Collectors.groupingBy(Chunk::getDocumentId, LinkedHashMap::new, Collectors.toList()));
        return byDoc.entrySet().stream()
                .map(e -> new DocumentSummary(e.getKey(), e.getValue().size(), firstMetadata(e.getValue())))
                .toList();
    }

    private static Map<String, Object> firstMetadata(List<Chunk> chunks) {
        var first = chunks.stream()
                .filter(c -> !c.getMetadata().isEmpty())
                .findFirst()
                .orElse(null);
        return first == null ? Map.of() : first.getMetadata();
    }

    private List<Chunk> search(float[] queryEmbedding, int topK, String documentId) {
        if (topK <= 0) {
            return List.of();
        }
        int fetch = ContentDeduplicator.fetchSize(topK);
        var best = new java.util.PriorityQueue<Scored>(Math.min(fetch, 64),
                Comparator.comparingDouble(Scored::score));
        for (var chunk : chunksById.values()) {
            if (documentId != null && !documentId.equals(chunk.getDocumentId())) {
                continue;
            }
            best.offer(new Scored(chunk, cosine(queryEmbedding, chunk.getEmbedding())));
            if (best.size() > fetch) {
                best.poll();
            }
        }
        var hits = best.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .map(Scored::chunk)
                .toList();
        var distinct = ContentDeduplicator.distinct(hits, topK);
        log.debug("In-memory search returned {} hits ({} distinct)", hits.size(), distinct.size());
        return distinct;
    }

    private static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Scored(Chunk chunk, double score) {}
}