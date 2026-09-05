package com.rag.common.adapter;

import com.rag.common.services.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Adapter bridging the domain {@link ChatModel} interface onto Spring AI's
 * {@link ChatClient}. Keeps business logic decoupled from the concrete provider
 * (DIP): the Spring AI bean resolves to Ollama, OpenAI, etc. by active profile.
 */
public class SpringAiChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatModel.class);

    private final ChatClient chatClient;

    public SpringAiChatModel(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String complete(String prompt) {
        String answer = chatClient.prompt().user(prompt).call().content();
        log.debug("Chat complete: promptLength={}, answerLength={}", prompt.length(),
                answer == null ? 0 : answer.length());
        return answer;
    }

    @Override
    public Flux<String> completeStream(String prompt) {
        log.debug("Chat stream started: promptLength={}", prompt.length());
        return chatClient.prompt().user(prompt).stream().content();
    }
}