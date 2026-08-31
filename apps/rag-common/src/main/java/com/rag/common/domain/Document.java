package com.rag.common.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Core document entity. Represents an ingested source document with metadata.
 */
public final class Document {

    private final String id;
    private final String content;
    private final Map<String, Object> metadata;
    private final Instant createdAt;

    public Document(String id, String content, Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id, "document id must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document document)) return false;
        return id.equals(document.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}