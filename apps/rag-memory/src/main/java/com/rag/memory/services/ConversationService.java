package com.rag.memory.services;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
import com.rag.memory.domain.ChatMessageEntity;
import com.rag.memory.domain.ConversationEntity;
import com.rag.memory.repositories.ConversationRepository;
import com.rag.memory.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;

    public ConversationService(ConversationRepository conversationRepo,
                               MessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    public List<ConversationDTO> listConversations() {
        List<ConversationDTO> conversations = conversationRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(ConversationService::toContract)
                .toList();
        log.debug("Listed {} conversations", conversations.size());
        return conversations;
    }

    public ConversationDTO createConversation(String title) {
        String resolvedTitle = title == null || title.isBlank() ? "New conversation" : title.trim();
        var conversation = conversationRepo.save(
                new ConversationEntity(UUID.randomUUID().toString(),
                        resolvedTitle, OffsetDateTime.now()));
        log.info("Created conversation {} ('{}')", conversation.getId(), conversation.getTitle());
        return toContract(conversation);
    }

    public List<ChatMessageDTO> listMessages(String conversationId) {
        List<ChatMessageDTO> messages = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ConversationService::toContract)
                .toList();
        log.debug("Listed {} messages for conversation {}", messages.size(), conversationId);
        return messages;
    }

    public ChatMessageDTO addMessage(String conversationId, ChatMessageDTO message) {
        if (!conversationRepo.existsById(conversationId)) {
            log.warn("Add-message rejected: unknown conversation {}", conversationId);
            throw new IllegalArgumentException("Unknown conversation: " + conversationId);
        }
        var saved = messageRepo.save(new ChatMessageEntity(
                UUID.randomUUID().toString(), conversationId, message.getRole().getValue(),
                message.getContent(), OffsetDateTime.now()));
        log.info("Added message {} to conversation {}", saved.getId(), conversationId);
        return toContract(saved);
    }

    private static ConversationDTO toContract(ConversationEntity entity) {
        return new ConversationDTO().id(entity.getId()).title(entity.getTitle()).createdAt(entity.getCreatedAt());
    }

    private static ChatMessageDTO toContract(ChatMessageEntity entity) {
        return new ChatMessageDTO().id(entity.getId()).conversationId(entity.getConversationId())
                .role(ChatMessageDTO.RoleEnum.fromValue(entity.getRole()))
                .content(entity.getContent()).createdAt(entity.getCreatedAt());
    }
}