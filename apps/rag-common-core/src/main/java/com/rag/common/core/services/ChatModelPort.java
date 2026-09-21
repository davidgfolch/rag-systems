package com.rag.common.core.services;

/**
 * Strategy interface (port) for one-shot LLM chat generation.
 *
 * <p>Implementations wrap different providers (Ollama, OpenAI), selected via
 * configuration profiles. Business logic depends only on this interface (DIP,
 * OCP), keeping the generation layer decoupled from any concrete provider.
 *
 * <p>Token-by-token streaming is declared separately by
 * {@code StreamingChatModelPort} so this core module stays free of reactive
 * (reactor-core) dependencies.
 */
public interface ChatModelPort {

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt the prompt text (including retrieved context)
     * @return the generated completion
     */
    String complete(String prompt);
}
