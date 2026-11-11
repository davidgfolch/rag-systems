package com.rag.common.ingestion.crawl;

import com.rag.contract.model.CrawlStartRequest;
import com.rag.contract.model.CrawlStepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.*;

class CrawlControllerTest {

    private static final String URL = "https://example.com/root";
    private static final String SESSION = "s1";

    private final CrawlSessionService service = mock(CrawlSessionService.class);
    private final CrawlController sut = new CrawlController(service);

    @Test
    void startsCrawlWithDefaults() {
        when(service.start(URL, 5, 50, true, true)).thenReturn(startResult());
        var res = sut.start(new CrawlStartRequest().url(URI.create(URL)));
        assertThat(res.getStatusCode()).isEqualTo(CREATED);
        assertThat(res.getBody().getSessionId()).isEqualTo(SESSION);
        assertThat(res.getBody().getPage().getUrl()).isEqualTo(URL);
        assertThat(res.getBody().getInitialRemaining()).isEqualTo(1);
        assertThat(res.getBody().getDiscoveredPages()).isEqualTo(4);
        assertThat(res.getBody().getHasMore()).isTrue();
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
        var res = sut.start(new CrawlStartRequest().url(URI.create("")));
        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void stepsCrawl() {
        when(service.step(SESSION, "q", false)).thenReturn(stepResult());
        var res = sut.step(SESSION, new CrawlStepRequest().question("q"));
        assertThat(res.getStatusCode()).isEqualTo(OK);
        assertThat(res.getBody().getDocumentId()).isEqualTo("d1");
        assertThat(res.getBody().getPageUrl()).isEqualTo(URL);
        assertThat(res.getBody().getIngested()).isTrue();
        assertThat(res.getBody().getSkippedLinks()).isEqualTo(2);
        assertThat(res.getBody().getNextPage().getUrl()).isEqualTo(URL + "/next");
    }

    @Test
    void stepsCrawlWithOverride() {
        when(service.step(SESSION, "q", true)).thenReturn(stepResult());
        sut.step(SESSION, new CrawlStepRequest().question("q").override(true));
        verify(service).step(SESSION, "q", true);
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
        assertThat(sut.cancel(SESSION).getStatusCode()).isEqualTo(NO_CONTENT);
    }

    @Test
    void cancelUnknownSessionReturnsNotFound() {
        when(service.cancel(SESSION)).thenReturn(false);
        assertThat(sut.cancel(SESSION).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void mapsUnknownSessionToNotFound() {
        var res = sut.handleUnknownSession(new CrawlSessionService.SessionNotFoundException(SESSION));
        assertThat(res.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void mapsNoPageToConflict() {
        var res = sut.handleNoPage(new CrawlSessionService.NoPageException(SESSION));
        assertThat(res.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void mapsFetchFailureToBadGateway() {
        var res = sut.handleFetchFailure(new RestClientException("down"));
        assertThat(res.getStatusCode()).isEqualTo(BAD_GATEWAY);
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
