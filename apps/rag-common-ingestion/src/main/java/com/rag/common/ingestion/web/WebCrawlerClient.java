package com.rag.common.ingestion.web;

import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.RetryableStatuses;
import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.PageDTO;
import com.rag.contract.model.RankLinksRequest;
import com.rag.contract.model.RankLinksResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.rag.contract.constants.ApiPaths.FETCH;
import static com.rag.contract.constants.ApiPaths.RANK_LINKS;

/**
 * Client for the shared rag-webcrawler tool. The RAG modules orchestrate URL
 * ingestion by fetching pages and ranking their outbound links here before
 * chunking/embedding them itself, and the agent's web tool reuses it to ground
 * answers on live page text.
 *
 * <p>Calls are retried on transient failures (timeouts, connection resets,
 * 408/425/429 and 5xx) with the configured {@link RetryPolicy}.
 */
public class WebCrawlerClient implements WebPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerClient.class);

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final RetryExecutor retry;

    public WebCrawlerClient(RestClient restClient, RetryPolicy retryPolicy, RetryObserver observer) {
        this.restClient = restClient;
        this.retry = new RetryExecutor(Objects.requireNonNull(retryPolicy, "retryPolicy must not be null"),
                WebCrawlerClient::isRetryable, observer, Thread::sleep);
    }

    /**
     * Builds a client with production timeouts and the default retry schedule.
     * The supplied builder (Spring Boot's auto-configured one) is cloned so the
     * shared bean is left untouched.
     */
    public static WebCrawlerClient create(RestClient.Builder builder, String baseUrl, RetryObserver observer) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(DEFAULT_CONNECT_TIMEOUT));
        requestFactory.setReadTimeout(toMillis(DEFAULT_READ_TIMEOUT));
        log.debug("Building webcrawler client for {} (connect={} ms, read={} ms)",
                baseUrl, toMillis(DEFAULT_CONNECT_TIMEOUT), toMillis(DEFAULT_READ_TIMEOUT));
        var client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        return new WebCrawlerClient(client, RetryPolicy.defaults(), observer);
    }

    @Override
    public PageDTO fetch(String url) {
        log.info("Fetching URL {} via webcrawler", url);
        var request = new FetchRequest(URI.create(url));
        return retry.call("fetch " + url, () -> restClient.post()
                .uri(FETCH)
                .body(request)
                .retrieve()
                .body(PageDTO.class));
    }

    @Override
    public List<String> rankLinks(List<String> links, String question) {
        if (links == null || links.isEmpty()) return List.of();
        log.debug("Ranking {} links via webcrawler (question={})", links.size(), question);
        var body = new RankLinksRequest(links).question(question);
        var res = retry.call("rank " + links.size() + " links", () -> restClient.post()
                .uri(RANK_LINKS)
                .body(body)
                .retrieve()
                .body(RankLinksResponse.class));
        return Optional.ofNullable(res).map(RankLinksResponse::getLinks).orElseGet(List::of);
    }

    @Override
    public List<String> discover(String url) {
        return retry.call("discover " + url, () -> WebCrawlerDiscovery.discover(restClient, url));
    }

    static boolean isRetryable(Throwable failure) {
        if (failure instanceof RestClientResponseException http) {
            return RetryableStatuses.isRetryable(http.getStatusCode().value());
        }
        return failure instanceof ResourceAccessException;
    }

    private static int toMillis(Duration timeout) {
        return Math.toIntExact(Math.min(timeout.toMillis(), Integer.MAX_VALUE));
    }
}
