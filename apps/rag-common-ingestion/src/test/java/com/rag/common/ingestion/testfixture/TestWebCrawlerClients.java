package com.rag.common.ingestion.testfixture;

import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.ingestion.web.WebCrawlerClient;
import org.springframework.web.client.RestClient;

/** Builds {@link WebCrawlerClient} instances through the production constructor. */
public final class TestWebCrawlerClients {

    private TestWebCrawlerClients() {
    }

    public static WebCrawlerClient noRetry(RestClient restClient) {
        return new WebCrawlerClient(restClient, RetryPolicy.noRetry(), RetryObserver.NOOP);
    }

    public static WebCrawlerClient retrying(RestClient restClient, RetryPolicy retryPolicy) {
        return new WebCrawlerClient(restClient, retryPolicy, RetryObserver.NOOP);
    }
}
