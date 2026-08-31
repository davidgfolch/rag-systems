package com.rag.common.domain;

import java.util.List;
import java.util.Map;

/**
 * A chunk is a segmented piece of a document, ready for embedding and storage.
 */
public final class Chunk {

    private final String id;
    private final String documentId;
    private final String content;
    private final int index;
    private final Map<String, Object> metadata;
    private List<Float> embedding;

    public Chunk(String id, String documentId, String content, int index, Map<String, Object> metadata) {
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.index = index;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getContent() {
        return content;
    }

    public int getIndex() {
        return index;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }
}