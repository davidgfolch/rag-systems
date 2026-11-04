package com.rag.agentic.services;

import com.rag.common.ingestion.web.WebPageFetcher;
import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.PageDTO;
import com.rag.contract.model.RankLinksRequest;
import com.rag.contract.model.RankLinksResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static com.rag.contract.constants.ApiPaths.FETCH;
import static com.rag.contract.constants.ApiPaths.RANK_LINKS;

/**
 * Client for the shared rag-webcrawler tool. The rag-module orchestrates URL
 * ingestion by fetching pages and ranking their outbound links here before
 * chunking/embedding them itself, and the agent's web tool reuses it to ground
 * answers on live page text.
 */
public class WebCrawlerClient implements WebPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerClient.class);

    private final RestClient restClient;

    public WebCrawlerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PageDTO fetch(String url) {
        log.info("Fetching URL {} via webcrawler", url);
        var request = new FetchRequest(URI.create(url));
        return restClient.post()
                .uri(FETCH)
                .body(request)
                .retrieve()
                .body(PageDTO.class);
    }

    @Override
    public List<String> rankLinks(List<String> links, String question) {
        if (links == null || links.isEmpty()) return List.of();
        log.debug("Ranking {} links via webcrawler (question={})", links.size(), question);
        var response = restClient.post()
                .uri(RANK_LINKS)
                .body(new RankLinksRequest(links).question(question))
                .retrieve()
                .body(RankLinksResponse.class);
        return Optional.ofNullable(response).map(RankLinksResponse::getLinks).orElseGet(List::of);
    }
}
