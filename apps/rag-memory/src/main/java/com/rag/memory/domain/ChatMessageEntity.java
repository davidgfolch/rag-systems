package com.rag.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "messages", schema = "rag_memory")
public class ChatMessageEntity {

    @Id
    private String id;

    @Column(name = "conversation_id")
    private String conversationId;

    private String role;

    private String content;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String id, String conversationId, String role, String content, OffsetDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}