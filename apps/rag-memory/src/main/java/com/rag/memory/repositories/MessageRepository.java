package com.rag.memory.repositories;

import com.rag.memory.domain.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}