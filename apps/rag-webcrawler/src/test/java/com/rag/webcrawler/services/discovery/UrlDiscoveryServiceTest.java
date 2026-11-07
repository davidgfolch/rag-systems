package com.rag.webcrawler.services.discovery;

import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlDiscoveryServiceTest {

    private static final String START = "https://example.com/docs/";

    private final WebPageFetcher fetcher = mock(WebPageFetcher.class);
    private final SitemapUrlDiscovery sitemaps = mock(SitemapUrlDiscovery.class);
    private final UrlDiscoveryService sut = new UrlDiscoveryService(fetcher, sitemaps, 100);

    @Test
    void unionsNavigationAndSitemapUrls() {
        when(fetcher.fetch(START)).thenReturn(page(START, START + "a", "https://other.com/x"));
        when(sitemaps.discover(START)).thenReturn(List.of(START + "b", START + "a"));

        var urls = sut.discover(START);

        assertThat(urls).containsExactlyInAnyOrder(START + "a", START + "b");
    }

    @Test
    void fallsBackToAllLinksWhenNavigationIsEmpty() {
        when(fetcher.fetch(START)).thenReturn(page(START, START + "a"));
        when(sitemaps.discover(START)).thenReturn(List.of());

        assertThat(sut.discover(START)).containsExactly(START + "a");
    }

    @Test
    void keepsGoingWhenNavigationDiscoveryFails() {
        when(fetcher.fetch(anyString())).thenThrow(new IllegalStateException("down"));
        when(sitemaps.discover(START)).thenReturn(List.of(START + "b"));

        assertThat(sut.discover(START)).containsExactly(START + "b");
    }

    @Test
    void excludesTheStartUrlAndCollapsesVersionedCopies() {
        when(fetcher.fetch(START)).thenReturn(page(START, START));
        when(sitemaps.discover(START)).thenReturn(List.of(
                START + "3.0/a", START + "3.1/a"));

        var urls = sut.discover(START);

        assertThat(urls).singleElement().asString().contains("3.0/a");
    }

    @Test
    void appliesTheRequestedLimit() {
        when(fetcher.fetch(START)).thenReturn(page(START, START + "a", START + "b", START + "c"));
        when(sitemaps.discover(START)).thenReturn(List.of());

        assertThat(sut.discover(START, 2)).hasSize(2);
    }

    private static PageDTO page(String url, String... navLinks) {
        return new PageDTO(url, "Docs", "text").navLinks(List.of(navLinks));
    }
}
