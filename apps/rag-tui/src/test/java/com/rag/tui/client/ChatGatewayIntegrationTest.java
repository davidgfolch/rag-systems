package com.rag.tui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.WsChatStubApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ChatGateway} against a real embedded Tomcat
 * WebSocket endpoint, proving token streaming and the final answer are
 * delivered over an actual socket.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WsChatStubApp.class,
        properties = "spring.main.web-application-type=servlet"
)
class ChatGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void streamsTokensAndFinalAnswerOverWebSocket() {
        ModuleRegistry registry = new ModuleRegistry(
                List.of(new Module("rag-basic", "http://localhost:" + port)), "rag-basic");
        ChatGateway sut = new ChatGateway(registry, new StandardWebSocketClient(), new ObjectMapper(), 60);
        List<String> tokens = new ArrayList<>();

        String answer = sut.ask("what is rag", 4, tokens::add);

        assertThat(answer).isEqualTo("Hello World");
        assertThat(tokens).containsExactly("Hello ");
    }
}