package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.Page;

/**
 * Strategy interface for fetching a web page and extracting its plain text.
 */
public interface WebPageFetcher {

    Page fetch(String url);
}