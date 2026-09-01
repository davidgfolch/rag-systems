package com.rag.tui.client;

import com.rag.contract.model.Conversation;
import com.rag.contract.model.ChatMessage;
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

    public List<Conversation> conversations() {
        Conversation[] conversations = restClient.get()
                .uri("/api/conversations")
                .retrieve()
                .body(Conversation[].class);
        return conversations == null ? List.of() : List.of(conversations);
    }

    public List<ChatMessage> messages(String conversationId) {
        ChatMessage[] messages = restClient.get()
                .uri("/api/conversations/{id}/messages", conversationId)
                .retrieve()
                .body(ChatMessage[].class);
        return messages == null ? List.of() : List.of(messages);
    }
}