package com.rag.webcrawler.api;

import com.rag.contract.model.RankLinksRequest;
import com.rag.contract.model.RankLinksResponse;
import com.rag.webcrawler.services.WebCrawlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.rag.contract.constants.ApiPaths.RANK_LINKS;

/**
 * Ranking-only endpoint: the rag-module owns the crawl frontier, so it asks
 * rag-webcrawler to order candidate links without fetching them.
 */
@RestController
@RequestMapping(RANK_LINKS)
public class RankLinksController {

    private static final Logger log = LoggerFactory.getLogger(RankLinksController.class);

    private final WebCrawlService webCrawlService;

    public RankLinksController(WebCrawlService webCrawlService) {
        this.webCrawlService = webCrawlService;
    }

    @PostMapping
    public ResponseEntity<RankLinksResponse> rank(@RequestBody RankLinksRequest request) {
        log.info("Rank-links request for {} candidates", request.getLinks().size());
        var ranked = webCrawlService.rankLinks(request.getLinks(), request.getQuestion());
        return ResponseEntity.ok(new RankLinksResponse().links(ranked));
    }
}
