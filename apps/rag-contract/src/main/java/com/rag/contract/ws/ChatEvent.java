package com.rag.contract.ws;

/**
 * WebSocket frame: server -> client. type is "token", "done", or "error".
 */
public record ChatEvent(String type, String content, String conversationId) {
}