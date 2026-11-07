package com.rag.common.ingestion.crawl;

import com.rag.contract.model.CrawlStartRequest;
import com.rag.contract.model.CrawlStepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrawlControllerTest {

    private static final String URL = "https://example.com/root";
    private static final String SESSION = "s1";

    private final CrawlSessionService service = mock(CrawlSessionService.class);
    private final CrawlController sut = new CrawlController(service);

    @Test
    void startsCrawlWithDefaults() {
        when(service.start(URL, 5, 50, true, true)).thenReturn(startResult());
        var response = sut.start(new CrawlStartRequest().url(URI.create(URL)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getSessionId()).isEqualTo(SESSION);
        assertThat(response.getBody().getPage().getUrl()).isEqualTo(URL);
        assertThat(response.getBody().getInitialRemaining()).isEqualTo(1);
        assertThat(response.getBody().getDiscoveredPages()).isEqualTo(4);
        assertThat(response.getBody().getHasMore()).isTrue();
    }

    @Test
    void usesExplicitCrawlOptions() {
        when(service.start(URL, 2, 3, false, false)).thenReturn(startResult());
        sut.start(new CrawlStartRequest().url(URI.create(URL))
                .maxInitialLinks(2).maxPages(3).sameHostOnly(false).discover(false));
        verify(service).start(URL, 2, 3, false, false);
    }

    @Test
    void rejectsBlankUrl() {
        var response = sut.start(new CrawlStartRequest().url(URI.create("")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void stepsCrawl() {
        when(service.step(SESSION, "q")).thenReturn(stepResult());
        var response = sut.step(SESSION, new CrawlStepRequest().question("q"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDocumentId()).isEqualTo("d1");
        assertThat(response.getBody().getPageUrl()).isEqualTo(URL);
        assertThat(response.getBody().getIngested()).isTrue();
        assertThat(response.getBody().getSkippedLinks()).isEqualTo(2);
        assertThat(response.getBody().getNextPage().getUrl()).isEqualTo(URL + "/next");
    }

    @Test
    void stepWithoutBodyPassesNullQuestion() {
        when(service.step(SESSION, null)).thenReturn(stepResult());
        sut.step(SESSION, null);
        verify(service).step(SESSION, null);
    }

    @Test
    void cancelsKnownSession() {
        when(service.cancel(SESSION)).thenReturn(true);
        assertThat(sut.cancel(SESSION).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void cancelUnknownSessionReturnsNotFound() {
        when(service.cancel(SESSION)).thenReturn(false);
        assertThat(sut.cancel(SESSION).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mapsUnknownSessionToNotFound() {
        var response = sut.handleUnknownSession(new CrawlSessionService.SessionNotFoundException(SESSION));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mapsNoPageToConflict() {
        var response = sut.handleNoPage(new CrawlSessionService.NoPageException(SESSION));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsFetchFailureToBadGateway() {
        var response = sut.handleFetchFailure(new RestClientException("down"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private static CrawlSessionService.StartResult startResult() {
        return new CrawlSessionService.StartResult(SESSION,
                new CrawlSessionService.PageInfo(URL, "Root"), 1, true, 4);
    }

    private static CrawlSessionService.StepResult stepResult() {
        return new CrawlSessionService.StepResult(SESSION, "d1", URL, "Root", true, 0, true, 2,
                new CrawlSessionService.PageInfo(URL + "/next", "Next"));
    }
}
