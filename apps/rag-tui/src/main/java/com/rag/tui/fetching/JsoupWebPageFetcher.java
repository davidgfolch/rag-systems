package com.rag.tui.fetching;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;

/**
 * {@link WebPageFetcher} implementation backed by jsoup. Accepts a
 * {@link Connection} so tests can supply a mock without network access.
 */
public class JsoupWebPageFetcher implements WebPageFetcher {

    private final Connection connection;

    public JsoupWebPageFetcher() {
        this.connection = null;
    }

    public JsoupWebPageFetcher(Connection connection) {
        this.connection = connection;
    }

    @Override
    public WebPage fetch(String url) {
        try {
            Connection conn = connection != null ? connection : Jsoup.connect(url);
            String text = conn.get().text();
            return new WebPage(url, text);
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
