package com.rag.webcrawler.api;

import com.rag.contract.model.RankLinksRequest;
import com.rag.webcrawler.services.WebCrawlService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankLinksControllerTest {

    private final WebCrawlService service = mock(WebCrawlService.class);
    private final RankLinksController sut = new RankLinksController(service);

    @Test
    void ranksLinksForQuestion() {
        var links = List.of("https://ex.com/a", "https://ex.com/b");
        when(service.rankLinks(links, "q")).thenReturn(List.of("https://ex.com/b", "https://ex.com/a"));
        var res = sut.rank(new RankLinksRequest().links(links).question("q"));
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody().getLinks())
                .containsExactly("https://ex.com/b", "https://ex.com/a");
        verify(service).rankLinks(links, "q");
    }

    @Test
    void returnsEmptyListWhenServiceHasNothing() {
        when(service.rankLinks(null, null)).thenReturn(List.of());
        var res = sut.rank(new RankLinksRequest());
        assertThat(res.getBody().getLinks()).isEmpty();
    }
}
