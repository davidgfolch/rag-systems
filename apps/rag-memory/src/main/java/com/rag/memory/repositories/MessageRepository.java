package com.rag.memory.repositories;

import com.rag.memory.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}