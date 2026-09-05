package com.rag.webcrawler.services;

import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Orchestrates fetching and link prioritization for the web tool.
 */
public class WebCrawlService {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlService.class);

    private final WebPageFetcher fetcher;
    private final LinkPrioritizer linkPrioritizer;

    public WebCrawlService(WebPageFetcher fetcher, LinkPrioritizer linkPrioritizer) {
        this.fetcher = fetcher;
        this.linkPrioritizer = linkPrioritizer;
    }

    public PageDTO fetch(String url) {
        PageDTO page = fetcher.fetch(url);
        log.info("Fetched {} (title='{}', links={})", url, page.getTitle(),
                page.getLinks() == null ? 0 : page.getLinks().size());
        return page;
    }

    public List<PageDTO> fetchRelevantLinks(String url, String question, int topK) {
        log.info("Fetching up to {} relevant links for {} (question={})", topK, url, question);
        List<String> links = linkPrioritizer.prioritize(fetcher.fetch(url).getLinks(), question);
        return links.stream().limit(topK).map(fetcher::fetch).toList();
    }
}