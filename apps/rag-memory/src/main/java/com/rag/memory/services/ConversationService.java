package com.rag.memory.services;

import com.rag.contract.model.ChatMessage;
import com.rag.contract.model.Conversation;
import com.rag.memory.repositories.ConversationRepository;
import com.rag.memory.repositories.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public List<Conversation> listConversations() {
        return conversationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public Conversation createConversation(String title) {
        String resolvedTitle = title == null || title.isBlank() ? "New conversation" : title.trim();
        var conversation = conversationRepository.save(
                new com.rag.memory.domain.Conversation(UUID.randomUUID().toString(),
                        resolvedTitle, OffsetDateTime.now()));
        return toContract(conversation);
    }

    public List<ChatMessage> listMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public ChatMessage addMessage(String conversationId, ChatMessage message) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown conversation: " + conversationId));
        var saved = messageRepository.save(new com.rag.memory.domain.ChatMessage(
                UUID.randomUUID().toString(), conversationId, message.getRole().getValue(),
                message.getContent(), OffsetDateTime.now()));
        return toContract(saved);
    }

    private static Conversation toContract(com.rag.memory.domain.Conversation entity) {
        return new Conversation().id(entity.getId()).title(entity.getTitle()).createdAt(entity.getCreatedAt());
    }

    private static ChatMessage toContract(com.rag.memory.domain.ChatMessage entity) {
        return new ChatMessage().id(entity.getId()).conversationId(entity.getConversationId())
                .role(ChatMessage.RoleEnum.fromValue(entity.getRole()))
                .content(entity.getContent()).createdAt(entity.getCreatedAt());
    }
}