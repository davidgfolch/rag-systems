package com.rag.tui.client;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.ChatMessageDTO;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * REST client for rag-memory: conversation history owned outside the rag-* modules.
 */
public class MemoryClient {

    private final RestClient restClient;

    public MemoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ConversationDTO> conversations() {
        ConversationDTO[] conversations = restClient.get()
                .uri("/api/conversations")
                .retrieve()
                .body(ConversationDTO[].class);
        return conversations == null ? List.of() : List.of(conversations);
    }

    public List<ChatMessageDTO> messages(String conversationId) {
        ChatMessageDTO[] messages = restClient.get()
                .uri("/api/conversations/{id}/messages", conversationId)
                .retrieve()
                .body(ChatMessageDTO[].class);
        return messages == null ? List.of() : List.of(messages);
    }
}