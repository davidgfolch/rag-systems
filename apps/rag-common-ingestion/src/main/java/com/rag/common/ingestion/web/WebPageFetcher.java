package com.rag.common.ingestion.web;

import com.rag.contract.model.PageDTO;

/**
 * Functional seam for the page fetcher used by the shared
 * {@link IngestionController}. Each module wires its own web-crawler client via
 * a method reference ({@code webCrawlerClient::fetch}).
 */
@FunctionalInterface
public interface WebPageFetcher {

    PageDTO fetch(String url);
}