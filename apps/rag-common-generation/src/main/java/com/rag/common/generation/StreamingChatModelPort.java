package com.rag.common.generation;

import reactor.core.publisher.Flux;

/**
 * Strategy interface (port) for token-by-token streaming LLM chat generation.
 *
 * <p>Split out of {@link com.rag.common.core.services.ChatModelPort} so the core
 * module stays free of reactive (reactor-core) dependencies; only the generation
 * module depends on Project Reactor. Implementations wrap different providers
 * (Ollama, OpenAI), selected via configuration profiles.
 */
public interface StreamingChatModelPort {

    /**
     * Streams a completion for the given prompt, token by token.
     * Disposing the returned {@link Flux} cancels generation.
     *
     * @param prompt the prompt text
     * @return a streaming publisher of answer tokens
     */
    Flux<String> completeStream(String prompt);
}
