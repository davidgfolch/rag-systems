package com.rag.webcrawler.services.discovery;

import java.util.Optional;

/** Loads raw bytes for robots.txt and sitemap documents without following links. */
@FunctionalInterface
public interface UrlContentLoader {

    Optional<byte[]> load(String url);
}
