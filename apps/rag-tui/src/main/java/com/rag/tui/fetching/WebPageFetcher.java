package com.rag.tui.fetching;

/**
 * Strategy interface for fetching a web page and extracting its plain text.
 */
public interface WebPageFetcher {

    /**
     * Fetches the page at the given URL and returns its textual content.
     *
     * @param url the page URL
     * @return the fetched web page
     */
    WebPage fetch(String url);
}
