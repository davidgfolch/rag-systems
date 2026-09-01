package com.rag.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "conversations", schema = "rag_memory")
public class Conversation {

    @Id
    private String id;

    private String title;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Conversation() {
    }

    public Conversation(String id, String title, OffsetDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}