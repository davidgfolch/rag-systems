package com.rag.webcrawler.services;

import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Orchestrates fetching and link prioritization for the web tool.
 *
 * <p>Relevant pages are fetched concurrently (bounded, in priority order) so
 * the total latency is the slowest fetch instead of the sum of all of them.
 */
public class WebCrawlService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlService.class);

    static final int MAX_CONCURRENT_FETCHES = 8;

    private final WebPageFetcher fetcher;
    private final LinkPrioritizer linkPrioritizer;
    private final ExecutorService fetchExecutor;

    public WebCrawlService(WebPageFetcher fetcher, LinkPrioritizer linkPrioritizer) {
        this.fetcher = fetcher;
        this.linkPrioritizer = linkPrioritizer;
        this.fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public PageDTO fetch(String url) {
        PageDTO page = fetcher.fetch(url);
        log.info("Fetched {} (title='{}', links={})", url, page.getTitle(),
                page.getLinks() == null ? 0 : page.getLinks().size());
        return page;
    }

    public List<PageDTO> fetchRelevantLinks(String url, String question, int topK) {
        log.info("Fetching up to {} relevant links for {} (question={})", topK, url, question);
        List<String> links = linkPrioritizer.prioritize(fetcher.fetch(url).getLinks(), question)
                .stream().limit(topK).toList();
        if (links.isEmpty()) {
            return List.of();
        }
        var permits = new Semaphore(MAX_CONCURRENT_FETCHES);
        var futures = links.stream()
                .map(link -> CompletableFuture.supplyAsync(() -> fetchBounded(permits, link), fetchExecutor))
                .toList();
        return futures.stream().map(WebCrawlService::join).toList();
    }

    public List<String> rankLinks(List<String> links, String question) {
        if (links == null || links.isEmpty()) return List.of();
        List<String> ranked = linkPrioritizer.prioritize(links, question);
        log.info("Ranked {} candidate links (question={})", ranked.size(), question);
        return ranked;
    }

    private PageDTO fetchBounded(Semaphore permits, String link) {
        try {
            permits.acquire();
            try {
                return fetcher.fetch(link);
            } finally {
                permits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching " + link, e);
        }
    }

    private static PageDTO join(CompletableFuture<PageDTO> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    @Override
    public void close() {
        fetchExecutor.shutdownNow();
    }
}
