package com.rag.common.ingestion.web;

import com.rag.contract.model.PageDTO;

import java.util.List;

/**
 * Seam for the page fetcher used by the shared {@link IngestionController} and
 * the crawl service. Each module wires its own web-crawler client; the default
 * {@link #rankLinks(List, String)} keeps single-page fakes and legacy lambdas
 * working by preserving the discovered order.
 */
@FunctionalInterface
public interface WebPageFetcher {

    PageDTO fetch(String url);

    default List<String> rankLinks(List<String> links, String question) {
        return links == null ? List.of() : links;
    }

    default List<String> discover(String url) {
        return List.of();
    }
}
