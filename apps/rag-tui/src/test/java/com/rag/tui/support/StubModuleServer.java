package com.rag.tui.support;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP stub that impersonates a rag-* module for integration tests.
 * Serves the Actuator health endpoint plus the module REST API and records the
 * last ingest/crawl request bodies so tests can assert what the real client sent.
 */
public final class StubModuleServer {

    private static final String DEFAULT_CRAWL_START = "{\"sessionId\":\"c-1\","
            + "\"page\":{\"url\":\"https://example.com/root\",\"title\":\"Root\"},"
            + "\"initialRemaining\":0,\"hasMore\":false}";
    private static final String DEFAULT_CRAWL_STEP = "{\"sessionId\":\"c-1\","
            + "\"documentId\":\"i-1\",\"pageUrl\":\"https://example.com/root\",\"title\":\"Root\","
            + "\"ingested\":true,\"initialRemaining\":0,\"hasMore\":false,\"skippedLinks\":0}";
    private static final String DEFAULT_INGEST_STATUS =
            "{\"documentId\":\"i-1\",\"state\":\"COMPLETED\",\"chunkCount\":2}";

    private final HttpServer server;
    private final AtomicReference<byte[]> lastIngestBody = new AtomicReference<>();
    private final AtomicReference<String> lastIngestContentType = new AtomicReference<>();
    private final AtomicReference<String> documentsBody = new AtomicReference<>("[]");
    private final AtomicReference<String> lastDeletedDocumentId = new AtomicReference<>();
    private final AtomicReference<String> lastIngestUrl = new AtomicReference<>();
    private final AtomicReference<String> crawlStartBody = new AtomicReference<>(DEFAULT_CRAWL_START);
    private final Queue<String> crawlStepBodies = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> lastCrawlUrl = new AtomicReference<>();
    private final AtomicReference<String> lastCrawlStepBody = new AtomicReference<>();
    private final AtomicReference<String> lastCrawlCancelledSession = new AtomicReference<>();
    private final AtomicReference<String> ingestStatusBody = new AtomicReference<>(DEFAULT_INGEST_STATUS);
    private final AtomicReference<String> variantsBody = new AtomicReference<>("[]");
    private final AtomicReference<String> activationBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastActivatedVariant = new AtomicReference<>();
    private final AtomicReference<String> activeVariantBody = new AtomicReference<>("{}");
    private final AtomicReference<String> createdVariantBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastCreatedVariantBody = new AtomicReference<>();

    public StubModuleServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/actuator/health", exchange -> {
            respond(exchange, 200, "{\"status\":\"UP\"}");
        });
        server.createContext("/api/documents/ingest", exchange -> {
            lastIngestBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "{\"documentId\":\"i-1\",\"chunkCount\":2}");
        });
        server.createContext("/api/documents/ingest-url", exchange -> {
            lastIngestUrl.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "{\"documentId\":\"i-1\",\"chunkCount\":2}");
        });
        server.createContext("/api/documents/ingest-file", exchange -> {
            lastIngestBody.set(exchange.getRequestBody().readAllBytes());
            lastIngestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, "{\"documentId\":\"i-1\",\"chunkCount\":2}");
        });
        server.createContext("/api/documents/ingest-file-async", exchange -> {
            lastIngestBody.set(exchange.getRequestBody().readAllBytes());
            lastIngestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 202, "{\"documentId\":\"i-1\"}");
        });
        server.createContext("/api/documents/ingest-status", exchange -> {
            respond(exchange, 200, ingestStatusBody.get());
        });
        server.createContext("/api/documents/crawl", this::handleCrawl);
        server.createContext("/api/conversations", exchange -> {
            respond(exchange, 200, "[]");
        });
        server.createContext("/api/modules", this::handleVariants);
        server.createContext("/api/documents", exchange -> {
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                lastDeletedDocumentId.set(exchange.getRequestURI().getPath()
                        .replaceFirst("^/api/documents/", ""));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            respond(exchange, 200, documentsBody.get());
        });
        server.setExecutor(null);
        server.start();
    }

    private void handleCrawl(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            lastCrawlCancelledSession.set(path.replaceFirst("^/api/documents/crawl/", ""));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        if (path.endsWith("/step")) {
            lastCrawlStepBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, nextStepBody());
            return;
        }
        lastCrawlUrl.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 201, crawlStartBody.get());
    }

    private void handleVariants(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        var post = "POST".equalsIgnoreCase(exchange.getRequestMethod());
        if (post && path.endsWith("/activate")) {
            lastActivatedVariant.set(path.replaceAll("^.*/variants/([^/]+)/activate$", "$1"));
            respond(exchange, 200, activationBody.get());
            return;
        }
        if (post) {
            lastCreatedVariantBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, createdVariantBody.get());
            return;
        }
        if (path.endsWith("/active")) {
            respond(exchange, 200, activeVariantBody.get());
            return;
        }
        respond(exchange, 200, variantsBody.get());
    }

    private String nextStepBody() {
        var queued = crawlStepBodies.poll();
        return queued == null ? DEFAULT_CRAWL_STEP : queued;
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

    public String lastDeletedDocumentId() {
        return lastDeletedDocumentId.get();
    }

    public String lastIngestUrl() {
        return lastIngestUrl.get();
    }

    public String lastIngestContentType() {
        return lastIngestContentType.get();
    }

    public String lastCrawlUrl() {
        return lastCrawlUrl.get();
    }

    public String lastCrawlStepBody() {
        return lastCrawlStepBody.get();
    }

    public String lastCrawlCancelledSession() {
        return lastCrawlCancelledSession.get();
    }

    public void documents(String json) {
        documentsBody.set(json);
    }

    public void variants(String json) {
        variantsBody.set(json);
    }

    public void activationResponse(String json) {
        activationBody.set(json);
    }

    public String lastActivatedVariant() {
        return lastActivatedVariant.get();
    }

    public void activeVariant(String json) {
        activeVariantBody.set(json);
    }

    public void createdVariant(String json) {
        createdVariantBody.set(json);
    }

    public String lastCreatedVariantBody() {
        return lastCreatedVariantBody.get();
    }

    public void crawlStart(String json) {
        crawlStartBody.set(json);
    }

    public void crawlSteps(String... jsonBodies) {
        crawlStepBodies.clear();
        for (var body : jsonBodies) {
            crawlStepBodies.add(body);
        }
    }

    public void ingestStatus(String json) {
        ingestStatusBody.set(json);
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
