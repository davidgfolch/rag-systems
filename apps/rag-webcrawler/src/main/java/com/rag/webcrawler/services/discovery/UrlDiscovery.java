package com.rag.webcrawler.services.discovery;

import java.util.List;

/** Strategy for finding the crawlable pages of a site from one start URL. */
public interface UrlDiscovery {

    List<String> discover(String startUrl);
}
