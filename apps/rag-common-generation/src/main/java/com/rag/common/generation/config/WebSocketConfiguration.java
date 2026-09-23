package com.rag.common.generation.config;

import com.rag.common.generation.ws.ChatWebSocketHandler;
import com.rag.common.generation.ws.TraceHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the streaming chat handler at /ws/chat. Imported by each runnable
 * module's config alongside {@code RagPipelineConfiguration}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfiguration.class);

    private final ChatWebSocketHandler chatHandler;
    private final TraceHandshakeInterceptor traceHandshakeInterceptor;

    public WebSocketConfiguration(ChatWebSocketHandler chatHandler, TraceHandshakeInterceptor traceHandshakeInterceptor) {
        this.chatHandler = chatHandler;
        this.traceHandshakeInterceptor = traceHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat").addInterceptors(traceHandshakeInterceptor);
        log.debug("Registered chat handler at /ws/chat with trace handshake interceptor");
    }
}