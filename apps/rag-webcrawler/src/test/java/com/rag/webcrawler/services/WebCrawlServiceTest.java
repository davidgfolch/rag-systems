package com.rag.webcrawler.services;

import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebCrawlServiceTest {

    private final WebPageFetcher fetcher = mock(WebPageFetcher.class);
    private final LinkPrioritizer prioritizer = mock(LinkPrioritizer.class);
    private final WebCrawlService sut = new WebCrawlService(fetcher, prioritizer);

    @Test
    void fetchesSinglePage() {
        PageDTO page = new PageDTO("https://ex.com", "Title", "text");
        when(fetcher.fetch("https://ex.com")).thenReturn(page);
        PageDTO result = sut.fetch("https://ex.com");
        assertThat(result.getUrl()).isEqualTo("https://ex.com");
    }

    @Test
    void prioritizesAndFetchesTopLinks() {
        String base = "https://ex.com/page";
        List<String> links = List.of("https://ex.com/a", "https://ex.com/b", "https://ex.com/c");
        when(fetcher.fetch(base)).thenReturn(
                new PageDTO(base, "Title", "text").links(links));
        when(prioritizer.prioritize(links, "question"))
                .thenReturn(List.of("https://ex.com/c", "https://ex.com/a"));
        when(fetcher.fetch("https://ex.com/c")).thenReturn(new PageDTO("https://ex.com/c", "C", "c"));
        when(fetcher.fetch("https://ex.com/a")).thenReturn(new PageDTO("https://ex.com/a", "A", "a"));
        List<PageDTO> result = sut.fetchRelevantLinks(base, "question", 2);
        assertThat(result).extracting(PageDTO::getUrl)
                .containsExactly("https://ex.com/c", "https://ex.com/a");
    }

    @Test
    void respectsTopKLimit() {
        String base = "https://ex.com/page";
        List<String> links = List.of("https://ex.com/a", "https://ex.com/b");
        when(fetcher.fetch(base)).thenReturn(new PageDTO(base, "Title", "text").links(links));
        when(prioritizer.prioritize(links, null)).thenReturn(links);
        when(fetcher.fetch("https://ex.com/a")).thenReturn(new PageDTO("https://ex.com/a", "A", "a"));
        List<PageDTO> result = sut.fetchRelevantLinks(base, null, 1);
        assertThat(result).extracting(PageDTO::getUrl).containsExactly("https://ex.com/a");
        verify(fetcher).fetch(base);
    }

    @AfterEach
    void shutdownExecutor() {
        sut.close();
    }

    @Test
    void fetchesRelevantLinksConcurrently() {
        var base = "https://ex.com/page";
        var links = List.of("https://ex.com/a", "https://ex.com/b");
        var barrier = new CyclicBarrier(2);
        when(fetcher.fetch(base)).thenReturn(new PageDTO(base, "Title", "text").links(links));
        when(prioritizer.prioritize(links, "q")).thenReturn(links);
        for (var link : links) {
            when(fetcher.fetch(link)).thenAnswer(inv -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("fetches were not concurrent", e);
                }
                return new PageDTO(inv.getArgument(0), "T", "t");
            });
        }
        assertThat(sut.fetchRelevantLinks(base, "q", 2)).hasSize(2);
    }

    @Test
    void unwrapsFetchFailuresFromTheWorkerThread() {
        var base = "https://ex.com/page";
        var links = List.of("https://ex.com/a");
        when(fetcher.fetch(base)).thenReturn(new PageDTO(base, "Title", "text").links(links));
        when(prioritizer.prioritize(links, "q")).thenReturn(links);
        when(fetcher.fetch("https://ex.com/a")).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> sut.fetchRelevantLinks(base, "q", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void ranksLinksWithoutFetchingThem() {
        List<String> links = List.of("https://ex.com/a", "https://ex.com/b");
        when(prioritizer.prioritize(links, "q"))
                .thenReturn(List.of("https://ex.com/b", "https://ex.com/a"));
        assertThat(sut.rankLinks(links, "q"))
                .containsExactly("https://ex.com/b", "https://ex.com/a");
        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    void returnsEmptyWhenThereAreNoLinksToRank() {
        assertThat(sut.rankLinks(null, "q")).isEmpty();
        assertThat(sut.rankLinks(List.of(), "q")).isEmpty();
        verify(prioritizer, never()).prioritize(any(), any());
    }
}