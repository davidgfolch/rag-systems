package com.rag.tui.client;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.ChatMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.rag.contract.constants.ApiPaths.CONVERSATIONS;
import static com.rag.contract.constants.ApiPaths.CONVERSATIONS_MESSAGES;

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
        var conversations = restClient.get()
                .uri(CONVERSATIONS)
                .retrieve()
                .body(ConversationDTO[].class);
        log.debug("Fetched {} conversations from memory", conversations == null ? 0 : conversations.length);
        return conversations == null ? List.of() : List.of(conversations);
    }

    public List<ChatMessageDTO> messages(String conversationId) {
        var messages = restClient.get()
                .uri(CONVERSATIONS_MESSAGES, conversationId)
                .retrieve()
                .body(ChatMessageDTO[].class);
        log.debug("Fetched {} messages for conversation {}", messages == null ? 0 : messages.length, conversationId);
        return messages == null ? List.of() : List.of(messages);
    }
}