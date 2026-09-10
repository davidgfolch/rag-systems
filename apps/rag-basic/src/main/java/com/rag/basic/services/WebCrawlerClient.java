package com.rag.basic.services;

import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.PageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static com.rag.contract.constants.ApiPaths.FETCH;

/**
 * Client for the shared rag-webcrawler tool. The rag-module orchestrates URL
 * ingestion by fetching pages here before chunking/embedding them itself.
 */
public class WebCrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerClient.class);

    private final RestClient restClient;

    public WebCrawlerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public PageDTO fetch(String url) {
        log.info("Fetching URL {} via webcrawler", url);
        var request = new FetchRequest(URI.create(url));
        return restClient.post()
                .uri(FETCH)
                .body(request)
                .retrieve()
                .body(PageDTO.class);
    }
}