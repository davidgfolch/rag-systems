package com.rag.memory.repositories;

import com.rag.memory.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findAllByOrderByCreatedAtDesc();
}