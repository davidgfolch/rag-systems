package com.rag.webcrawler.services;

import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;

import java.util.List;

/**
 * Orchestrates fetching and link prioritization for the web tool.
 */
public class WebCrawlService {

    private final WebPageFetcher fetcher;
    private final LinkPrioritizer linkPrioritizer;

    public WebCrawlService(WebPageFetcher fetcher, LinkPrioritizer linkPrioritizer) {
        this.fetcher = fetcher;
        this.linkPrioritizer = linkPrioritizer;
    }

    public PageDTO fetch(String url) {
        return fetcher.fetch(url);
    }

    public List<PageDTO> fetchRelevantLinks(String url, String question, int topK) {
        List<String> links = linkPrioritizer.prioritize(fetcher.fetch(url).getLinks(), question);
        return links.stream().limit(topK).map(fetcher::fetch).toList();
    }
}