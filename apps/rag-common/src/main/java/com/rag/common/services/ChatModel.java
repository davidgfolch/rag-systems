package com.rag.common.services;

import java.util.List;

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
}
