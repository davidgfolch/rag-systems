package com.rag.common.services;

import reactor.core.publisher.Flux;

/**
 * Strategy interface for LLM chat generation.
 *
 * <p>Implementations wrap different providers (Ollama, OpenAI), selected via
 * configuration profiles. Business logic depends only on this interface (DIP,
 * OCP), keeping the generation layer decoupled from any concrete provider.
 */
public interface ChatModel {

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt the prompt text (including retrieved context)
     * @return the generated completion
     */
    String complete(String prompt);

    /**
     * Streams a completion for the given prompt, token by token.
     * Disposing the returned {@link Flux} cancels generation.
     *
     * @param prompt the prompt text
     * @return a streaming publisher of answer tokens
     */
    Flux<String> completeStream(String prompt);
}
