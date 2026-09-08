package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Minimal JSON-over-HTTP client for the rag-provider service. Uses only the JDK
 * {@link HttpClient} so rag-common needs no Spring Web dependency.
 */
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper) {
        this(baseUrl, HttpClient.newHttpClient(), objectMapper);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = trimSlash(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public <T> T postJson(String path, Object body, Class<T> responseType) {
        try {
            var request = HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response, responseType);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request failed for " + path + ": " + e.getMessage(), e);
        }
    }

    public <T> T get(String path, Class<T> responseType) {
        try {
            var request = HttpRequest.newBuilder(uri(path)).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response, responseType);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request failed for " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Opens a POST against an SSE endpoint. The returned stream must be closed
     * by the caller.
     */
    public InputStream postStream(String path, Object body) {
        try {
            var request = HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            log.debug("Opening provider stream at {}", path);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Provider stream failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider stream request failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private <T> T parse(HttpResponse<String> response, Class<T> responseType) {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Provider request failed: HTTP " + response.statusCode()
                    + " -> " + response.body());
        }
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse provider response: " + e.getMessage(), e);
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("provider base URL must not be blank");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}