package com.rag.contract.ws;

/**
 * WebSocket frame: server -> client. type is "token", "done", or "error".
 */
public record ChatResponse(String type, String content, String conversationId) {
}