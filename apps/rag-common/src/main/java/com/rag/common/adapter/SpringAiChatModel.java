package com.rag.common.adapter;

import com.rag.common.services.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Adapter bridging the domain {@link ChatModel} interface onto Spring AI's
 * {@link ChatClient}. Keeps business logic decoupled from the concrete provider
 * (DIP): the Spring AI bean resolves to Ollama, OpenAI, etc. by active profile.
 */
public class SpringAiChatModel implements ChatModel {

    private final ChatClient chatClient;

    public SpringAiChatModel(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String complete(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
    }

    @Override
    public Flux<String> completeStream(String prompt) {
        return chatClient.prompt().user(prompt).stream().content();
    }
}