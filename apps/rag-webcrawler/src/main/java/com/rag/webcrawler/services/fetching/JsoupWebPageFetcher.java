package com.rag.webcrawler.services.fetching;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.RetryableStatuses;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.rag.contract.model.PageDTO;

/**
 * {@link WebPageFetcher} implementation backed by jsoup. Accepts a
 * {@link Connection} so tests can supply a mock without network access.
 *
 * <p>Production wiring applies a fetch timeout and retries transient failures
 * (socket timeouts, connection resets, 408/425/429/5xx) with backoff. The legacy
 * constructors keep the historical no-retry behaviour.
 */
public class JsoupWebPageFetcher implements WebPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupWebPageFetcher.class);

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final String FAILURE_MESSAGE = "Failed to fetch URL: ";

    private final Connection connection;
    private final Duration timeout;
    private final RetryExecutor retry;
    private final PageBlockExtractor blockExtractor = new PageBlockExtractor();
    private final NavLinkExtractor navLinkExtractor = new NavLinkExtractor();

    public JsoupWebPageFetcher() {
        this(null, DEFAULT_TIMEOUT, RetryPolicy.noRetry());
    }

    public JsoupWebPageFetcher(Connection connection) {
        this(connection, DEFAULT_TIMEOUT, RetryPolicy.noRetry());
    }

    public JsoupWebPageFetcher(Duration timeout, RetryPolicy retryPolicy) {
        this(null, timeout, retryPolicy);
    }

    public JsoupWebPageFetcher(Connection connection, Duration timeout, RetryPolicy retryPolicy) {
        this.connection = connection;
        this.timeout = requirePositive(timeout);
        this.retry = new RetryExecutor(Objects.requireNonNull(retryPolicy, "retryPolicy must not be null"),
                JsoupWebPageFetcher::isRetryable);
    }

    @Override
    public PageDTO fetch(String url) {
        Connection conn = connection != null ? connection : Jsoup.connect(url);
        conn.timeout(toMillis(timeout));
        Document doc = retry.call("fetch " + url, () -> getPage(conn, url));
        List<String> links = new ArrayList<>();
        doc.select("a[href]").forEach(a -> links.add(a.absUrl("href")));
        String title = doc.title();
        var blocks = blockExtractor.extract(doc);
        var navLinks = navLinkExtractor.extract(doc);
        log.info("Fetched {} (title='{}', links={}, navLinks={}, blocks={})",
                url, title, links.size(), navLinks.size(), blocks.size());
        return new PageDTO(url, title, doc.text()).links(links).navLinks(navLinks).blocks(blocks);
    }

    private static Document getPage(Connection conn, String url) {
        try {
            return conn.get();
        } catch (IOException e) {
            throw new WebFetchException(FAILURE_MESSAGE + url, e);
        }
    }

    static boolean isRetryable(Throwable failure) {
        Throwable cause = failure instanceof WebFetchException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof HttpStatusException status) {
            return RetryableStatuses.isRetryable(status.getStatusCode());
        }
        return cause instanceof SocketTimeoutException || cause instanceof ConnectException
                || cause instanceof SocketException || cause instanceof UnknownHostException;
    }

    private static int toMillis(Duration value) {
        return Math.toIntExact(Math.min(value.toMillis(), Integer.MAX_VALUE));
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return value;
    }

    public static class WebFetchException extends RuntimeException {
        public WebFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
