package com.rag.common.ingestion.crawl;

import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.DocumentStorePort;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.web.WebPageFetcher;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlSessionServiceTest {

    private static final String ROOT = "https://example.com/root";

    private final Map<String, PageDTO> pages = new HashMap<>();
    private final WebPageFetcher fetcher = new WebPageFetcher() {
        @Override
        public PageDTO fetch(String url) {
            var page = pages.get(url);
            if (page == null) throw new IllegalStateException("no page for " + url);
            return page;
        }

        @Override
        public List<String> rankLinks(List<String> links, String question) {
            return links;
        }
    };
    private final AsyncIngestionService async = mock(AsyncIngestionService.class);
    private final DocumentStorePort documents = mock(DocumentStorePort.class);
    private final CrawlSessionService sut = new CrawlSessionService(fetcher, async, documents);

    @Test
    void startFetchesRootAndReturnsSession() {
        pages.put(ROOT, page(ROOT));
        var result = sut.start(ROOT, 5, 50, true);
        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.page().url()).isEqualTo(ROOT);
        assertThat(result.initialRemaining()).isEqualTo(1);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void stepSubmitsPageAndAddsRankedRootLinksToInitialSet() {
        pages.put(ROOT, page(ROOT, ROOT + "/a", ROOT + "/b", "https://other.com/c"));
        pages.put(ROOT + "/a", page(ROOT + "/a"));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 2, 50, true);
        var result = sut.step(start.sessionId(), "q");
        assertThat(result.ingested()).isTrue();
        assertThat(result.documentId()).isEqualTo("d1");
        assertThat(result.initialRemaining()).isEqualTo(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextPage().url()).isEqualTo(ROOT + "/a");
        assertThat(result.skippedLinks()).isEqualTo(1);
        verify(async).submitParsed(argThat(document -> ROOT.equals(document.metadata().get(SOURCE))));
    }

    @Test
    void stepSkipsAlreadyIngestedSource() {
        pages.put(ROOT, page(ROOT));
        when(documents.listDocuments())
                .thenReturn(List.of(new DocumentSummary("d0", 1, Map.<String, Object>of(SOURCE, ROOT))));
        var start = sut.start(ROOT, 5, 50, true);
        var result = sut.step(start.sessionId(), null);
        assertThat(result.ingested()).isFalse();
        assertThat(result.documentId()).isNull();
        verify(async, never()).submitParsed(any());
    }

    @Test
    void stepSkipsDuplicateAndOffHostLinks() {
        pages.put(ROOT, page(ROOT, ROOT + "/a", ROOT + "/a", "https://other.com/x"));
        pages.put(ROOT + "/a", page(ROOT + "/a"));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 5, 50, true);
        var result = sut.step(start.sessionId(), null);
        assertThat(result.skippedLinks()).isEqualTo(2);
        assertThat(result.initialRemaining()).isEqualTo(1);
    }

    @Test
    void initialSetIsCappedByMaxPages() {
        pages.put(ROOT, page(ROOT, ROOT + "/a", ROOT + "/b", ROOT + "/c"));
        pages.put(ROOT + "/a", page(ROOT + "/a"));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 5, 2, true);
        var rootStep = sut.step(start.sessionId(), null);
        assertThat(rootStep.initialRemaining()).isEqualTo(1);
        var secondStep = sut.step(start.sessionId(), null);
        assertThat(secondStep.initialRemaining()).isZero();
        assertThat(secondStep.hasMore()).isFalse();
    }

    @Test
    void linksFoundAfterInitialSetDoNotGrowInitialRemaining() {
        pages.put(ROOT, page(ROOT, ROOT + "/a"));
        pages.put(ROOT + "/a", page(ROOT + "/a", ROOT + "/a/x"));
        pages.put(ROOT + "/a/x", page(ROOT + "/a/x"));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 5, 50, true);
        assertThat(sut.step(start.sessionId(), null).initialRemaining()).isEqualTo(1);
        var childStep = sut.step(start.sessionId(), null);
        assertThat(childStep.initialRemaining()).isZero();
        assertThat(childStep.nextPage().url()).isEqualTo(ROOT + "/a/x");
    }

    @Test
    void fetchFailureIsSkippedAndFrontierContinues() {
        pages.put(ROOT, page(ROOT, ROOT + "/bad", ROOT + "/good"));
        pages.put(ROOT + "/good", page(ROOT + "/good"));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 5, 50, true);
        var result = sut.step(start.sessionId(), null);
        assertThat(result.nextPage().url()).isEqualTo(ROOT + "/good");
        assertThat(result.skippedLinks()).isEqualTo(1);
    }

    @Test
    void stepWithoutPageLeftThrowsNoPage() {
        pages.put(ROOT, page(ROOT));
        when(async.submitParsed(any())).thenReturn("d1");
        var start = sut.start(ROOT, 5, 1, true);
        assertThat(sut.step(start.sessionId(), null).hasMore()).isFalse();
        assertThatThrownBy(() -> sut.step(start.sessionId(), null))
                .isInstanceOf(CrawlSessionService.NoPageException.class);
    }

    @Test
    void cancelRemovesSession() {
        pages.put(ROOT, page(ROOT));
        var start = sut.start(ROOT, 5, 50, true);
        assertThat(sut.cancel(start.sessionId())).isTrue();
        assertThat(sut.cancel(start.sessionId())).isFalse();
        assertThatThrownBy(() -> sut.step(start.sessionId(), null))
                .isInstanceOf(CrawlSessionService.SessionNotFoundException.class);
    }

    @Test
    void unknownSessionThrows() {
        assertThatThrownBy(() -> sut.step("missing", null))
                .isInstanceOf(CrawlSessionService.SessionNotFoundException.class);
    }

    @Test
    void worksWithoutDocumentStore() {
        pages.put(ROOT, page(ROOT));
        when(async.submitParsed(any())).thenReturn("d1");
        var service = new CrawlSessionService(fetcher, async, null);
        var start = service.start(ROOT, 5, 50, true);
        assertThat(service.step(start.sessionId(), null).ingested()).isTrue();
    }

    private static PageDTO page(String url, String... links) {
        return new PageDTO(url, "Title " + url, "text " + url).links(List.of(links));
    }
}
