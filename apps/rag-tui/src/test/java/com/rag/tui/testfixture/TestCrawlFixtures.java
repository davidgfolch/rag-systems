package com.rag.tui.testfixture;

import com.rag.contract.model.CrawlPageDTO;
import com.rag.contract.model.CrawlStartResponse;
import com.rag.contract.model.CrawlStepResponse;

/** Shared crawl DTOs for the {@code add-url} command tests. */
public final class TestCrawlFixtures {

    public static final String URL = "https://x.com/page";
    public static final String SESSION = "s1";

    private TestCrawlFixtures() {
    }

    public static CrawlStartResponse start(int initialRemaining) {
        return new CrawlStartResponse().sessionId(SESSION).page(page(URL))
                .initialRemaining(initialRemaining).hasMore(true);
    }

    public static CrawlStepResponse step(String documentId, int initialRemaining, CrawlPageDTO next) {
        return new CrawlStepResponse().sessionId(SESSION).documentId(documentId)
                .pageUrl(URL).title("Page").ingested(documentId != null)
                .initialRemaining(initialRemaining).hasMore(next != null)
                .skippedLinks(0).nextPage(next);
    }

    public static CrawlStepResponse skippedStep(int initialRemaining, CrawlPageDTO next) {
        return new CrawlStepResponse().sessionId(SESSION)
                .pageUrl(URL).title("Page").ingested(false)
                .initialRemaining(initialRemaining).hasMore(next != null)
                .skippedLinks(0).nextPage(next);
    }

    public static CrawlPageDTO page(String url) {
        return new CrawlPageDTO(url, "Page");
    }
}
