package com.rag.tui.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP stub impersonating the rag-provider companion service for
 * integration tests: status, catalog (browse + refresh) and chat/embedding
 * switch endpoints. Records the last switch request body for assertions.
 */
public final class StubProviderServer {

    private final HttpServer server;
    private final AtomicReference<String> lastSwitchBody = new AtomicReference<>();

    public StubProviderServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/provider/catalog/refresh", exchange -> {
            respond(exchange, 200, catalogJson());
        });
        server.createContext("/api/provider/catalog", exchange -> {
            respond(exchange, 200, catalogJson());
        });
        server.createContext("/api/provider/chat", exchange -> {
            lastSwitchBody.set(readBody(exchange));
            respond(exchange, 204, "");
        });
        server.createContext("/api/provider/embedding", exchange -> {
            lastSwitchBody.set(readBody(exchange));
            respond(exchange, 204, "");
        });
        server.createContext("/api/provider", exchange -> {
            respond(exchange, 200, statusJson());
        });
        server.setExecutor(null);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://localhost:" + port();
    }

    public String lastSwitchBody() {
        return lastSwitchBody.get();
    }

    public void stop() {
        server.stop(0);
    }

    private static String statusJson() {
        return """
                {"chat":{"providerId":"ollama","model":"phi4"},
                 "embedding":{"providerId":"ollama","model":"nomic-embed-text"},
                 "provider":"ollama","embeddingDimension":768}""";
    }

    private static String catalogJson() {
        return """
                {"models":[
                  {"providerId":"ollama","modelId":"phi4","name":"Phi-4",
                   "limits":{"context":16384,"output":4096},
                   "capabilities":{"reasoning":true,"toolCall":false,"structuredOutput":false},
                   "cost":null,"status":null}],
                 "source":"https://models.dev/api.json",
                 "fetchedAt":"2026-09-08T10:00:00Z"}""";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}