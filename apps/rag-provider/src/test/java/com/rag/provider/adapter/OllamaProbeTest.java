package com.rag.provider.adapter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaProbeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String HOST = "127.0.0.1";

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void shouldAcceptAnySuccessfulStatus() throws IOException {
        server = server(200);
        assertThat(new OllamaProbe(TIMEOUT).isAlive(HOST, server.getAddress().getPort())).isTrue();
    }

    @Test
    void shouldAcceptNoContentStatus() throws IOException {
        server = server(204);
        assertThat(new OllamaProbe(TIMEOUT).isAlive(HOST, server.getAddress().getPort())).isTrue();
    }

    @Test
    void shouldRejectServerErrors() throws IOException {
        server = server(500);
        assertThat(new OllamaProbe(TIMEOUT).isAlive(HOST, server.getAddress().getPort())).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNothingListens() throws IOException {
        int port;
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(HOST, 0));
            port = socket.getLocalPort();
        }
        assertThat(new OllamaProbe(Duration.ofMillis(250)).isAlive(HOST, port)).isFalse();
    }

    @Test
    void shouldReturnFalseForMalformedHost() {
        assertThat(new OllamaProbe(TIMEOUT).isAlive("bad host", 11434)).isFalse();
    }

    private static HttpServer server(int status) throws IOException {
        var httpServer = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
        httpServer.createContext("/api/tags", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }
}
