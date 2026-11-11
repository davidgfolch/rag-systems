package com.rag.common.generation.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.tracing.TracePropagation;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal JSON-over-HTTP client for the rag-provider service. Uses only the JDK
 * {@link HttpClient} so rag-common-generation needs no Spring Web dependency. When a
 * {@link Tracer} is available the current span is propagated as a W3C
 * {@code traceparent} header so rag-provider keeps the same trace id.
 *
 * <p>Timeouts and retries come from {@link ProviderHttpSettings}; the legacy
 * constructors keep the historical 30-second, no-retry behaviour.
 */
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private static final String CONTENT_TYPE = "application/json";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String ACCEPT_EVENT_STREAM = "text/event-stream";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final int ERROR_STATUS_MIN = 400;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final ProviderHttpSettings settings;
    private final RetryExecutor retry;

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper) {
        this(baseUrl, objectMapper, null);
    }

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper, Tracer tracer) {
        this(baseUrl, objectMapper, tracer, ProviderHttpSettings.defaults());
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this(baseUrl, httpClient, objectMapper, null);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper, Tracer tracer) {
        this(baseUrl, httpClient, objectMapper, tracer, ProviderHttpSettings.defaults());
    }

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper, Tracer tracer,
                              ProviderHttpSettings settings) {
        this(baseUrl, defaultClient(settings.connectTimeout()), objectMapper, tracer, settings,
                RetryObserver.NOOP);
    }

    public ProviderHttpClient(String baseUrl, ObjectMapper objectMapper, Tracer tracer,
                              ProviderHttpSettings settings, RetryObserver observer) {
        this(baseUrl, defaultClient(settings.connectTimeout()), objectMapper, tracer, settings, observer);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper,
                              Tracer tracer, ProviderHttpSettings settings) {
        this(baseUrl, httpClient, objectMapper, tracer, settings, RetryObserver.NOOP);
    }

    public ProviderHttpClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper,
                              Tracer tracer, ProviderHttpSettings settings, RetryObserver observer) {
        this.baseUrl = trimSlash(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.settings = settings;
        this.retry = new RetryExecutor(ProviderHttpRetry.policy(settings), ProviderHttpRetry::isRetryable,
                observer, Thread::sleep);
    }

    public <T> T postJson(String path, Object body, Class<T> responseType) {
        var payload = serialize(body);
        try {
            return retry.call("POST " + path, () -> sendJson(path, payload, responseType));
        } catch (ProviderTransportException e) {
            throw wrap("Provider request", path, e);
        }
    }

    public <T> T get(String path, Class<T> responseType) {
        try {
            return retry.call("GET " + path, () -> sendGet(path, responseType));
        } catch (ProviderTransportException e) {
            throw wrap("Provider request", path, e);
        }
    }

    /**
     * Opens a POST against an SSE endpoint. The returned stream must be closed
     * by the caller. Only the connection phase is retried; once the body is
     * streaming, a failure surfaces to the consumer.
     */
    public InputStream postStream(String path, Object body) {
        var payload = serialize(body);
        try {
            return retry.call("POST stream " + path, () -> openStream(path, payload));
        } catch (ProviderTransportException e) {
            throw wrap("Provider stream request", path, e);
        }
    }

    private <T> T sendJson(String path, String payload, Class<T> responseType) {
        var request = request(path)
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return send(request, responseType);
    }

    private <T> T sendGet(String path, Class<T> responseType) {
        return send(request(path).GET().build(), responseType);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            var res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(res, responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ProviderTransportException.interrupted(e);
        } catch (IOException e) {
            throw ProviderTransportException.failed(e);
        }
    }

    private InputStream openStream(String path, String payload) {
        var request = request(path)
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE)
                .header(ACCEPT_HEADER, ACCEPT_EVENT_STREAM)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            log.debug("Opening provider stream at {}", path);
            var res = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() >= ERROR_STATUS_MIN) {
                closeQuietly(res.body());
                throw new ProviderHttpException(res.statusCode(),
                        "Provider stream failed: HTTP " + res.statusCode(), retryAfter(res));
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ProviderTransportException.interrupted(e);
        } catch (IOException e) {
            throw ProviderTransportException.failed(e);
        }
    }

    private HttpRequest.Builder request(String path) {
        return withTracePropagation(HttpRequest.newBuilder(uri(path)).timeout(settings.requestTimeout()));
    }

    private static HttpClient defaultClient(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
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

    private <T> T parse(HttpResponse<String> res, Class<T> responseType) {
        if (res.statusCode() >= ERROR_STATUS_MIN) {
            throw new ProviderHttpException(res.statusCode(),
                    "Provider request failed: HTTP " + res.statusCode() + " -> " + res.body(),
                    retryAfter(res));
        }
        try {
            return objectMapper.readValue(res.body(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse provider response: " + e.getMessage(), e);
        }
    }

    private String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize provider request: " + e.getMessage(), e);
        }
    }

    private IllegalStateException wrap(String action, String path, ProviderTransportException failure) {
        var state = failure.isInterrupted() ? "interrupted" : "failed";
        return new IllegalStateException(action + " " + state + " for " + path + " at " + baseUrl
                + ": " + describe(failure.getCause()), failure.getCause());
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException e) {
            log.debug("Could not close failed provider stream body: {}", e.getMessage());
        }
    }

    private static Duration retryAfter(HttpResponse<?> res) {
        return res.headers().firstValue(RETRY_AFTER_HEADER)
                .flatMap(RetryAfter::parse)
                .orElse(null);
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
