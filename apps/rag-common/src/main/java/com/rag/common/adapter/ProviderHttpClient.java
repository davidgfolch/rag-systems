package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.tracing.TracePropagation;
import io.micrometer.tracing.Tracer;
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
 * {@link HttpClient} so rag-common needs no Spring Web dependency. When a
 * {@link Tracer} is available the current span is propagated as a W3C
 * {@code traceparent} header so rag-provider keeps the same trace id.
 */
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper) {
        this(baseUrl, HttpClient.newHttpClient(), objectMapper, null);
    }

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper, Tracer tracer) {
        this(baseUrl, HttpClient.newHttpClient(), objectMapper, tracer);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this(baseUrl, httpClient, objectMapper, null);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper, Tracer tracer) {
        this.baseUrl = trimSlash(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    public <T> T postJson(String path, Object body, Class<T> responseType) {
        try {
            var request = withTracePropagation(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response, responseType);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Provider request failed for " + path + " at " + baseUrl + ": " + describe(e), e);
        }
    }

    public <T> T get(String path, Class<T> responseType) {
        try {
            var request = withTracePropagation(HttpRequest.newBuilder(uri(path))).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response, responseType);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Provider request failed for " + path + " at " + baseUrl + ": " + describe(e), e);
        }
    }

    /**
     * Opens a POST against an SSE endpoint. The returned stream must be closed
     * by the caller.
     */
    public InputStream postStream(String path, Object body) {
        try {
            var request = withTracePropagation(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))))
                    .build();
            log.debug("Opening provider stream at {}", path);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Provider stream failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Provider stream request failed for " + path + " at " + baseUrl + ": " + describe(e), e);
        }
    }

    private HttpRequest.Builder withTracePropagation(HttpRequest.Builder builder) {
        var span = tracer != null ? tracer.currentSpan() : null;
        if (span != null) {
            String traceparent = TracePropagation.w3cTraceparent(span.context());
            if (traceparent != null) {
                builder.header(TracePropagation.TRACEPARENT_HEADER, traceparent);
            }
        }
        return builder;
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

    private static String describe(Throwable e) {
        return e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("provider base URL must not be blank");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}