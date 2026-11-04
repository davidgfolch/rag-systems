package com.rag.provider.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Liveness check for an Ollama server: {@code GET /api/tags} answering 2xx.
 * Only the status matters — the model list is not parsed here, so any Ollama
 * version (or compatible server) is accepted.
 */
public class OllamaProbe {

    private static final Logger log = LoggerFactory.getLogger(OllamaProbe.class);
    private static final String TAGS_PATH = "/api/tags";
    private static final String URL_FORMAT = "http://%s:%d%s";
    private static final int OK_MIN = 200;
    private static final int OK_MAX = 300;

    private final HttpClient client;
    private final Duration timeout;

    public OllamaProbe(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** True when {@code host:port} serves the Ollama API. */
    public boolean isAlive(String host, int port) {
        try {
            var request = HttpRequest.newBuilder(URI.create(String.format(URL_FORMAT, host, port, TAGS_PATH)))
                    .timeout(timeout)
                    .GET()
                    .build();
            var status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= OK_MIN && status < OK_MAX;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            log.debug("Ollama probe failed for {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }
}
