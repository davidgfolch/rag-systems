package com.rag.contract.ws;

/**
 * WebSocket frame: client -> server. type is "ask" or "cancel".
 */
public record ChatRequest(String type, String question, Integer topK, String conversationId) {
}