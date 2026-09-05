package com.rag.tui.client;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.ChatMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * REST client for rag-memory: conversation history owned outside the rag-* modules.
 */
public class MemoryClient {

    private static final Logger log = LoggerFactory.getLogger(MemoryClient.class);

    private final RestClient restClient;

    public MemoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ConversationDTO> conversations() {
        ConversationDTO[] conversations = restClient.get()
                .uri("/api/conversations")
                .retrieve()
                .body(ConversationDTO[].class);
        log.debug("Fetched {} conversations from memory", conversations == null ? 0 : conversations.length);
        return conversations == null ? List.of() : List.of(conversations);
    }

    public List<ChatMessageDTO> messages(String conversationId) {
        ChatMessageDTO[] messages = restClient.get()
                .uri("/api/conversations/{id}/messages", conversationId)
                .retrieve()
                .body(ChatMessageDTO[].class);
        log.debug("Fetched {} messages for conversation {}", messages == null ? 0 : messages.length, conversationId);
        return messages == null ? List.of() : List.of(messages);
    }
}