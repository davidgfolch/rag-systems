package com.rag.common.ingestion.crawl;

import com.rag.contract.model.CrawlPageDTO;
import com.rag.contract.model.CrawlStartRequest;
import com.rag.contract.model.CrawlStartResponse;
import com.rag.contract.model.CrawlStepRequest;
import com.rag.contract.model.CrawlStepResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

import static com.rag.contract.constants.ApiPaths.CRAWL;

/**
 * REST surface for the interactive URL crawl. Kept separate from
 * {@code IngestionController} so both stay focused; the session state lives in
 * {@link CrawlSessionService} inside the active rag-module.
 */
@RestController
@RequestMapping(CRAWL)
public class CrawlController {

    private static final Logger log = LoggerFactory.getLogger(CrawlController.class);

    private final CrawlSessionService crawlSessionService;

    public CrawlController(CrawlSessionService crawlSessionService) {
        this.crawlSessionService = crawlSessionService;
    }

    @PostMapping
    public ResponseEntity<CrawlStartResponse> start(@RequestBody CrawlStartRequest request) {
        if (Objects.toString(request.getUrl(), "").isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var result = crawlSessionService.start(request.getUrl().toString(),
                intOr(request.getMaxInitialLinks(), CrawlSessionService.DEFAULT_MAX_INITIAL_LINKS),
                intOr(request.getMaxPages(), CrawlSessionService.DEFAULT_MAX_PAGES),
                request.getSameHostOnly() == null || request.getSameHostOnly(),
                request.getDiscover() == null || request.getDiscover());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CrawlStartResponse()
                .sessionId(result.sessionId())
                .page(pageDto(result.page()))
                .initialRemaining(result.initialRemaining())
                .discoveredPages(result.discoveredPages())
                .hasMore(result.hasMore()));
    }

    @PostMapping("/{sessionId}/step")
    public ResponseEntity<CrawlStepResponse> step(@PathVariable String sessionId,
                                                  @RequestBody(required = false) CrawlStepRequest request) {
        var result = crawlSessionService.step(sessionId, request == null ? null : request.getQuestion());
        return ResponseEntity.ok(new CrawlStepResponse()
                .sessionId(result.sessionId())
                .documentId(result.documentId())
                .pageUrl(result.pageUrl())
                .title(result.title())
                .ingested(result.ingested())
                .initialRemaining(result.initialRemaining())
                .hasMore(result.hasMore())
                .skippedLinks(result.skippedLinks())
                .nextPage(pageDto(result.nextPage())));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> cancel(@PathVariable String sessionId) {
        return crawlSessionService.cancel(sessionId)
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(CrawlSessionService.SessionNotFoundException.class)
    public ResponseEntity<String> handleUnknownSession(CrawlSessionService.SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(CrawlSessionService.NoPageException.class)
    public ResponseEntity<String> handleNoPage(CrawlSessionService.NoPageException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<String> handleFetchFailure(RestClientException e) {
        log.warn("Crawl page fetch failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Could not fetch page: " + e.getMessage());
    }

    private static int intOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static CrawlPageDTO pageDto(CrawlSessionService.PageInfo page) {
        return page == null ? null : new CrawlPageDTO(page.url(), page.title());
    }
}
