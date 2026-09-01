package com.rag.memory.services;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
import com.rag.memory.domain.ChatMessageEntity;
import com.rag.memory.domain.ConversationEntity;
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

    public List<ConversationDTO> listConversations() {
        return conversationRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public ConversationDTO createConversation(String title) {
        String resolvedTitle = title == null || title.isBlank() ? "New conversation" : title.trim();
        var conversation = conversationRepo.save(
                new ConversationEntity(UUID.randomUUID().toString(),
                        resolvedTitle, OffsetDateTime.now()));
        return toContract(conversation);
    }

    public List<ChatMessageDTO> listMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ConversationService::toContract)
                .toList();
    }

    public ChatMessageDTO addMessage(String conversationId, ChatMessageDTO message) {
        if (!conversationRepo.existsById(conversationId)) {
            throw new IllegalArgumentException("Unknown conversation: " + conversationId);
        }
        var saved = messageRepo.save(new ChatMessageEntity(
                UUID.randomUUID().toString(), conversationId, message.getRole().getValue(),
                message.getContent(), OffsetDateTime.now()));
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