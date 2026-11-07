package com.rag.common.ingestion.web;

import com.rag.contract.model.DiscoverRequest;
import com.rag.contract.model.DiscoverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

import static com.rag.contract.constants.ApiPaths.DISCOVER;

/**
 * Shared client-side discovery call used by every module's web-crawler
 * adapter. Keeping it here avoids duplicating the endpoint wiring across the
 * rag-basic/advanced/agentic modules.
 */
public final class WebCrawlerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerDiscovery.class);

    private WebCrawlerDiscovery() {}

    public static List<String> discover(RestClient restClient, String url) {
        log.info("Discovering crawl URLs for {} via webcrawler", url);
        var response = restClient.post()
                .uri(DISCOVER)
                .body(new DiscoverRequest(URI.create(url)))
                .retrieve()
                .body(DiscoverResponse.class);
        return response == null ? List.of() : response.getUrls();
    }
}
