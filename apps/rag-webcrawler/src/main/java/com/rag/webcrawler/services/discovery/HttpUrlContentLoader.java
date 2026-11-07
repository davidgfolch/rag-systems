package com.rag.webcrawler.services.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * HTTP loader for robots.txt and sitemap files. Transparently decodes gzip
 * responses (servers often send sitemaps compressed even without {@code .gz}).
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

    public HttpUrlContentLoader() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public HttpUrlContentLoader(HttpClient client) {
        this.client = client;
    }

    @Override
    public Optional<byte[]> load(String url) {
        var uri = toUri(url);
        if (uri.isEmpty()) return Optional.empty();
        try {
            var req = HttpRequest.newBuilder(uri.get()).timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT).header("Accept", "*/*").GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() < SUCCESS_MIN || res.statusCode() >= SUCCESS_MAX) {
                log.debug("Loader got HTTP {} for {}", res.statusCode(), url);
                return Optional.empty();
            }
            return Optional.of(decode(res.body()));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading {}", url);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Could not load {}: {}", url, e.getMessage());
            return Optional.empty();
        }
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
}
