package com.rag.webcrawler.api;

import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.Page;
import com.rag.webcrawler.services.WebCrawlService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchControllerTest {

    private final WebCrawlService service = mock(WebCrawlService.class);
    private final FetchController sut = new FetchController(service);

    @Test
    void fetchesSinglePage() {
        Page page = new Page("https://ex.com", "T", "text");
        when(service.fetch("https://ex.com")).thenReturn(page);

        ResponseEntity<Page> result = sut.fetch(new FetchRequest(URI.create("https://ex.com")));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getUrl()).isEqualTo("https://ex.com");
    }

    @Test
    void fetchesRelevantLinks() {
        when(service.fetchRelevantLinks("https://ex.com", "q", 5))
                .thenReturn(List.of(new Page("https://ex.com/a", "A", "a")));

        com.rag.contract.model.FetchLinksRequest request =
                new com.rag.contract.model.FetchLinksRequest(URI.create("https://ex.com")).question("q");
        ResponseEntity<List<Page>> result = sut.fetchLinks(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).hasSize(1);
        verify(service).fetchRelevantLinks("https://ex.com", "q", 5);
    }
}