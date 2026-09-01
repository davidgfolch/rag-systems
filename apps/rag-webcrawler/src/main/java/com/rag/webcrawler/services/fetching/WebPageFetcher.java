package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.PageDTO;

/**
 * Strategy interface for fetching a web page and extracting its plain text.
 */
public interface WebPageFetcher {

    PageDTO fetch(String url);
}