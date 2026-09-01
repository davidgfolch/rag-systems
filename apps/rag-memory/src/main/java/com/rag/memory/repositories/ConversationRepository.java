package com.rag.memory.repositories;

import com.rag.memory.domain.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findAllByOrderByCreatedAtDesc();
}