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

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;

    public ConversationService(ConversationRepository conversationRepo,
                               MessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    public List<Conversation> listConversations() {
        return conversationRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public Conversation createConversation(String title) {
        String resolvedTitle = title == null || title.isBlank() ? "New conversation" : title.trim();
        var conversation = conversationRepo.save(
                new com.rag.memory.domain.Conversation(UUID.randomUUID().toString(),
                        resolvedTitle, OffsetDateTime.now()));
        return toContract(conversation);
    }

    public List<ChatMessage> listMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public ChatMessage addMessage(String conversationId, ChatMessage message) {
        if (!conversationRepo.existsById(conversationId)) {
            throw new IllegalArgumentException("Unknown conversation: " + conversationId);
        }
        var saved = messageRepo.save(new com.rag.memory.domain.ChatMessage(
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