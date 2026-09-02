package com.rag.tui.support;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP stub that impersonates a rag-* module for integration tests.
 * Serves the Actuator health endpoint plus the module REST API and records the
 * last ingest request body so tests can assert what the real client sent.
 */
public final class StubModuleServer {

    private final HttpServer server;
    private final AtomicReference<byte[]> lastIngestBody = new AtomicReference<>();
    private final AtomicReference<String> lastIngestContentType = new AtomicReference<>();

    public StubModuleServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/actuator/health", exchange -> {
            respond(exchange, 200, "{\"status\":\"UP\"}");
        });
        server.createContext("/api/documents/ingest", exchange -> {
            lastIngestBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "{\"documentId\":\"i-1\",\"chunkCount\":2}");
        });
        server.createContext("/api/documents/ingest-file", exchange -> {
            lastIngestBody.set(exchange.getRequestBody().readAllBytes());
            lastIngestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, "{\"documentId\":\"i-1\",\"chunkCount\":2}");
        });
        server.createContext("/api/conversations", exchange -> {
            respond(exchange, 200, "[]");
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

    public byte[] lastIngestBody() {
        return lastIngestBody.get();
    }

    public String lastIngestContentType() {
        return lastIngestContentType.get();
    }

    public void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}