package com.rag.basic.config;

import com.rag.basic.api.chat.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the streaming chat handler at /ws/chat.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final TraceHandshakeInterceptor traceHandshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatHandler, TraceHandshakeInterceptor traceHandshakeInterceptor) {
        this.chatHandler = chatHandler;
        this.traceHandshakeInterceptor = traceHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat").addInterceptors(traceHandshakeInterceptor);
    }
}