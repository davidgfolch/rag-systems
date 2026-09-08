package com.rag.provider.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * LLM chat generation against the currently active chat model.
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ModelRouter router;

    public ChatService(ModelRouter router) {
        this.router = router;
    }

    public String complete(String prompt) {
        var answer = router.chatModel().call(new Prompt(prompt))
                .getResult().getOutput().getText();
        log.info("Chat completion generated: promptLength={}, answerLength={}",
                prompt.length(), answer == null ? 0 : answer.length());
        return answer;
    }

    public Flux<String> stream(String prompt) {
        log.info("Chat stream started: promptLength={}", prompt.length());
        return router.chatModel().stream(new Prompt(prompt))
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput().getText())
                .filter(Objects::nonNull);
    }
}