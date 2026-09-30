package com.rag.common.generation.ws;

import reactor.core.publisher.Flux;

/**
 * Functional seam over the streaming chat services: rag-basic/rag-advanced use
 * {@code ChatService#askStream}, rag-agentic uses {@code AgenticChatService#askStream}.
 * Allows one shared {@link ChatWebSocketHandler} to serve every module.
 */
@FunctionalInterface
public interface AnswerStreamer {

    Flux<String> askStream(String question, int topK);
}