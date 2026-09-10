package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.contract.constants.ApiPaths;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteChatModelPortTest {

    private HttpServer server;
    private RemoteChatModelPort port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        var mapper = new ObjectMapper();
        var client = new ProviderHttpClient("http://localhost:" + server.getAddress().getPort(), mapper);
        port = new RemoteChatModelPort(client, mapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldCompleteViaApiComplete() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 200, "application/json", "{\"answer\":\"Hello!\"}"));
        assertThat(port.complete("hi")).isEqualTo("Hello!");
    }

    @Test
    void shouldThrowOnServerError() {
        server.createContext(ApiPaths.COMPLETE,
                exchange -> respond(exchange, 500, "text/plain", "boom"));
        assertThatThrownBy(() -> port.complete("hi")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldStreamTokensAndCompleteOnDone() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                """
                        data:{"type":"token","content":"Hel"}
                        data:{"type":"token","content":"lo"}
                        data:{"type":"done","content":null,"conversationId":null}
                        """));
        StepVerifier.create(port.completeStream("hi"))
                .expectNext("Hel", "lo")
                .verifyComplete();
    }

    @Test
    void shouldFailOnProviderErrorFrame() {
        server.createContext(ApiPaths.CHAT_STREAM, exchange -> respond(exchange, 200, "text/event-stream",
                "data:{\"type\":\"error\",\"content\":\"nope\",\"conversationId\":null}\n\n"));
        StepVerifier.create(port.completeStream("hi"))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("nope"))
                .verify();
    }

    private static void respond(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}