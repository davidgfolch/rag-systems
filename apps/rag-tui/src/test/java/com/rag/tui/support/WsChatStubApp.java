package com.rag.tui.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Minimal Spring Boot application used only for the {@code ChatGateway} web
 * integration test: starts an embedded Tomcat with a stubbed /ws/chat endpoint
 * and deliberately does NOT include the rag-tui production configuration (whose
 * terminal runner would block on stdin). The {@link ServerEndpointExporter} is
 * declared explicitly because Spring Boot does not register it automatically.
 */
@SpringBootApplication
public class WsChatStubApp {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}