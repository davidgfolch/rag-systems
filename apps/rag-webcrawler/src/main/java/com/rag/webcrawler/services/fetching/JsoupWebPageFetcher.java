package com.rag.webcrawler.services.fetching;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.rag.contract.model.PageDTO;

/**
 * {@link WebPageFetcher} implementation backed by jsoup. Accepts a
 * {@link Connection} so tests can supply a mock without network access.
 */
public class JsoupWebPageFetcher implements WebPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupWebPageFetcher.class);

    private final Connection connection;
    private final PageBlockExtractor blockExtractor = new PageBlockExtractor();
    private final NavLinkExtractor navLinkExtractor = new NavLinkExtractor();

    public JsoupWebPageFetcher() {
        this.connection = null;
    }

    public JsoupWebPageFetcher(Connection connection) {
        this.connection = connection;
    }

    @Override
    public PageDTO fetch(String url) {
        try {
            Connection conn = connection != null ? connection : Jsoup.connect(url);
            Document doc = conn.get();
            List<String> links = new ArrayList<>();
            doc.select("a[href]").forEach(a -> links.add(a.absUrl("href")));
            String title = doc.title();
            var blocks = blockExtractor.extract(doc);
            var navLinks = navLinkExtractor.extract(doc);
            log.info("Fetched {} (title='{}', links={}, navLinks={}, blocks={})",
                    url, title, links.size(), navLinks.size(), blocks.size());
            return new PageDTO(url, title, doc.text()).links(links).navLinks(navLinks).blocks(blocks);
        } catch (IOException e) {
            throw new WebFetchException("Failed to fetch URL: " + url, e);
        }
    }

    public static class WebFetchException extends RuntimeException {
        public WebFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}