package com.rag.webcrawler.services.discovery;

import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.RetryableStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * HTTP loader for robots.txt and sitemap files. Transparently decodes gzip
 * responses (servers often send sitemaps compressed even without {@code .gz}).
 *
 * <p>Transient failures (timeouts, connection resets, 408/425/429/5xx) are
 * retried with backoff when a {@link RetryPolicy} is supplied; the legacy
 * constructors keep the historical single-attempt behaviour.
 */
public class HttpUrlContentLoader implements UrlContentLoader {

    private static final Logger log = LoggerFactory.getLogger(HttpUrlContentLoader.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "rag-webcrawler/1.0";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final int SUCCESS_MIN = 200;
    private static final int SUCCESS_MAX = 300;
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;

    private final HttpClient client;
    private final RetryExecutor retry;

    public HttpUrlContentLoader() {
        this(defaultClient(), RetryPolicy.noRetry());
    }

    public HttpUrlContentLoader(HttpClient client) {
        this(client, RetryPolicy.noRetry());
    }

    public HttpUrlContentLoader(RetryPolicy retryPolicy) {
        this(defaultClient(), retryPolicy, RetryObserver.NOOP);
    }

    public HttpUrlContentLoader(RetryPolicy retryPolicy, RetryObserver observer) {
        this(defaultClient(), retryPolicy, observer);
    }

    public HttpUrlContentLoader(HttpClient client, RetryPolicy retryPolicy) {
        this(client, retryPolicy, RetryObserver.NOOP);
    }

    public HttpUrlContentLoader(HttpClient client, RetryPolicy retryPolicy, RetryObserver observer) {
        this.client = client;
        this.retry = new RetryExecutor(Objects.requireNonNull(retryPolicy, "retryPolicy must not be null"),
                HttpUrlContentLoader::isRetryable, observer);
    }

    @Override
    public Optional<byte[]> load(String url) {
        var uri = toUri(url);
        if (uri.isEmpty()) return Optional.empty();
        try {
            var res = retry.call("load " + url, () -> send(uri.get()));
            if (res.statusCode() < SUCCESS_MIN || res.statusCode() >= SUCCESS_MAX) {
                log.debug("Loader got HTTP {} for {}", res.statusCode(), url);
                return Optional.empty();
            }
            return Optional.of(decode(res.body()));
        } catch (TransientStatus e) {
            log.debug("Loader gave up on {} after HTTP {}", url, e.statusCode());
            return Optional.empty();
        } catch (InterruptedLoad _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading {}", url);
            return Optional.empty();
        } catch (IoFailure | IOException e) {
            log.warn("Could not load {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpResponse<byte[]> send(URI uri) {
        try {
            var req = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT).header("Accept", "*/*").GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (RetryableStatuses.isRetryable(res.statusCode())) {
                throw new TransientStatus(res.statusCode());
            }
            return res;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedLoad();
        } catch (IOException e) {
            throw new IoFailure(e);
        }
    }

    static boolean isRetryable(Throwable failure) {
        if (failure instanceof TransientStatus status) {
            return RetryableStatuses.isRetryable(status.statusCode());
        }
        if (failure instanceof IoFailure io) {
            return isTransientIo(io.getCause());
        }
        return false;
    }

    private static boolean isTransientIo(Throwable cause) {
        return cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                || cause instanceof ConnectException || cause instanceof SocketException
                || cause instanceof UnknownHostException;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    private static Optional<URI> toUri(String url) {
        try {
            var uri = URI.create(url);
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase(HTTP) || scheme.equalsIgnoreCase(HTTPS))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static byte[] decode(byte[] body) throws IOException {
        if (body.length < 2 || body[0] != GZIP_MAGIC_FIRST || (body[1] & 0xff) != GZIP_MAGIC_SECOND) return body;
        try (var in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }

    private static final class TransientStatus extends RuntimeException {
        private final int statusCode;

        private TransientStatus(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    private static final class IoFailure extends RuntimeException {
        private IoFailure(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static final class InterruptedLoad extends RuntimeException {
        private InterruptedLoad() {
            super("interrupted");
        }
    }
}
