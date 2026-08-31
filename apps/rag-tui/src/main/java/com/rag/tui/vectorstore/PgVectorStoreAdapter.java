package com.rag.tui.vectorstore;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter bridging the domain {@link VectorStore} interface onto Spring AI's
 * {@link org.springframework.ai.vectorstore.VectorStore} (backed by PgVector).
 * Spring AI embeds documents and queries internally via its own embedding model.
 */
public class PgVectorStoreAdapter implements VectorStore {

    private final org.springframework.ai.vectorstore.VectorStore delegate;

    public PgVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public void add(List<Chunk> chunks) {
        delegate.add(chunks.stream().map(this::toSpringDocument).toList());
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
        return delegate.similaritySearch(request).stream().map(this::toChunk).toList();
    }

    private org.springframework.ai.document.Document toSpringDocument(Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkIndex", chunk.getIndex());
        return org.springframework.ai.document.Document.builder()
                .id(chunk.getId())
                .text(chunk.getContent())
                .metadata(metadata)
                .build();
    }

    private Chunk toChunk(org.springframework.ai.document.Document doc) {
        Object docId = doc.getMetadata().get("documentId");
        Object index = doc.getMetadata().get("chunkIndex");
        return new Chunk(
                doc.getId(),
                docId == null ? "unknown" : String.valueOf(docId),
                doc.getText(),
                index == null ? 0 : Integer.parseInt(String.valueOf(index)),
                doc.getMetadata());
    }
}
